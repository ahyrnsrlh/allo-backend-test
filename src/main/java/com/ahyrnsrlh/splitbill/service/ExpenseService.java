package com.ahyrnsrlh.splitbill.service;

import com.ahyrnsrlh.splitbill.dto.request.CreateExpenseRequest;
import com.ahyrnsrlh.splitbill.dto.request.ShareRequest;
import com.ahyrnsrlh.splitbill.dto.response.ExpenseResponse;
import com.ahyrnsrlh.splitbill.dto.response.ShareResponse;
import com.ahyrnsrlh.splitbill.entity.*;
import com.ahyrnsrlh.splitbill.exception.BusinessException;
import com.ahyrnsrlh.splitbill.exception.ResourceNotFoundException;
import com.ahyrnsrlh.splitbill.repository.ExpenseRepository;
import com.ahyrnsrlh.splitbill.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


@Service
@Transactional(readOnly = true)
public class ExpenseService {

    /** Monetary scale used throughout share calculations. */
    private static final int MONETARY_SCALE = 2;

    private final ExpenseRepository expenseRepository;
    private final ParticipantRepository participantRepository;
    private final GroupService groupService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ParticipantRepository participantRepository,
                          GroupService groupService) {
        this.expenseRepository = expenseRepository;
        this.participantRepository = participantRepository;
        this.groupService = groupService;
    }

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request) {
        BillGroup group = groupService.findGroupById(groupId);

        SplitType splitType = parseSplitType(request.splitType());

        Participant payer = findParticipantInGroup(request.paidByParticipantId(), groupId, "Payer");

        validateNoDuplicateParticipants(request.shares());

        Map<Long, Participant> participantMap = resolveShareParticipants(request.shares(), groupId);

        Expense expense = new Expense();
        expense.setDescription(request.description());
        expense.setAmount(request.amount().setScale(MONETARY_SCALE, RoundingMode.HALF_UP));
        expense.setSplitType(splitType);
        expense.setGroup(group);
        expense.setPaidBy(payer);

        List<ExpenseShare> shares = buildShares(splitType, request, expense, participantMap);
        expense.getShares().addAll(shares);

        Expense saved = expenseRepository.save(expense);

        return toExpenseResponse(saved);
    }

    public List<ExpenseResponse> getExpenses(Long groupId) {
        groupService.findGroupById(groupId);
        return expenseRepository.findByGroupIdWithPayer(groupId).stream()
                .map(this::toExpenseResponse)
                .toList();
    }

    private SplitType parseSplitType(String raw) {
        try {
            return SplitType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported split type: " + raw + ". Valid values: EQUAL, EXACT, PERCENTAGE");
        }
    }

    private Participant findParticipantInGroup(Long participantId, Long groupId, String role) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        role + " participant not found with id: " + participantId));
        if (!participant.getGroup().getId().equals(groupId)) {
            throw new BusinessException(role + " participant does not belong to group " + groupId);
        }
        return participant;
    }

    private void validateNoDuplicateParticipants(List<ShareRequest> shares) {
        Set<Long> seen = new HashSet<>();
        for (ShareRequest share : shares) {
            if (!seen.add(share.participantId())) {
                throw new BusinessException(
                        "Duplicate participant id in shares: " + share.participantId());
            }
        }
    }

    private Map<Long, Participant> resolveShareParticipants(List<ShareRequest> shares, Long groupId) {
        Map<Long, Participant> map = new LinkedHashMap<>();
        for (ShareRequest sr : shares) {
            Participant p = findParticipantInGroup(sr.participantId(), groupId, "Share participant");
            map.put(p.getId(), p);
        }
        return map;
    }

    private List<ExpenseShare> buildShares(SplitType splitType,
                                           CreateExpenseRequest request,
                                           Expense expense,
                                           Map<Long, Participant> participantMap) {
        return switch (splitType) {
            case EQUAL -> buildEqualShares(request, expense, participantMap);
            case EXACT -> buildExactShares(request, expense, participantMap);
            case PERCENTAGE -> buildPercentageShares(request, expense, participantMap);
        };
    }

    /**
     * Equal split: divides amount by participant count using FLOOR rounding, then adds the
     * remainder (in smallest currency units) to the first participants deterministically.
     * This guarantees the sum of shares equals the original expense amount exactly.
     */
    private List<ExpenseShare> buildEqualShares(CreateExpenseRequest request,
                                                Expense expense,
                                                Map<Long, Participant> participantMap) {
        BigDecimal total = expense.getAmount();
        int count = participantMap.size();

        BigDecimal baseShare = total.divide(BigDecimal.valueOf(count), MONETARY_SCALE, RoundingMode.FLOOR);
        BigDecimal remainder = total.subtract(baseShare.multiply(BigDecimal.valueOf(count)));

        // smallest currency unit at the configured scale (e.g., 0.01 for scale=2)
        BigDecimal unit = BigDecimal.ONE.scaleByPowerOfTen(-MONETARY_SCALE);
        long remainderUnits = remainder.divide(unit, 0, RoundingMode.FLOOR).longValueExact();

        List<ExpenseShare> shares = new ArrayList<>();
        int index = 0;
        for (Participant participant : participantMap.values()) {
            BigDecimal share = baseShare;
            if (index < remainderUnits) {
                share = share.add(unit);
            }
            shares.add(createShare(expense, participant, share));
            index++;
        }
        return shares;
    }

    /**
     * Exact split: validates that the provided amounts sum exactly to the expense total.
     */
    private List<ExpenseShare> buildExactShares(CreateExpenseRequest request,
                                                Expense expense,
                                                Map<Long, Participant> participantMap) {
        BigDecimal total = expense.getAmount();
        BigDecimal sharesSum = BigDecimal.ZERO;

        List<ExpenseShare> shares = new ArrayList<>();
        for (ShareRequest sr : request.shares()) {
            if (sr.amount() == null) {
                throw new BusinessException(
                        "amount is required for each share in EXACT split");
            }
            BigDecimal shareAmount = sr.amount().setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
            sharesSum = sharesSum.add(shareAmount);
            shares.add(createShare(expense, participantMap.get(sr.participantId()), shareAmount));
        }

        if (sharesSum.compareTo(total) != 0) {
            throw new BusinessException(
                    "Exact shares must equal the expense amount. Expected: " + total +
                    ", got: " + sharesSum);
        }
        return shares;
    }

    /**
     * Percentage split: converts percentages to monetary amounts using HALF_UP rounding,
     * then corrects any rounding remainder by adjusting the first participant's share
     * deterministically so the total equals the expense amount exactly.
     */
    private List<ExpenseShare> buildPercentageShares(CreateExpenseRequest request,
                                                     Expense expense,
                                                     Map<Long, Participant> participantMap) {
        BigDecimal total = expense.getAmount();

        BigDecimal percentageSum = request.shares().stream()
                .map(sr -> {
                    if (sr.percentage() == null) {
                        throw new BusinessException(
                                "percentage is required for each share in PERCENTAGE split");
                    }
                    return sr.percentage();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (percentageSum.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessException(
                    "Percentages must sum to 100. Got: " + percentageSum);
        }

        List<BigDecimal> rawAmounts = request.shares().stream()
                .map(sr -> total.multiply(sr.percentage())
                        .divide(BigDecimal.valueOf(100), MONETARY_SCALE, RoundingMode.HALF_UP))
                .toList();

        BigDecimal rawSum = rawAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = total.subtract(rawSum);

        // Adjust the first participant's share to absorb any rounding remainder
        List<BigDecimal> adjustedAmounts = new ArrayList<>(rawAmounts);
        adjustedAmounts.set(0, adjustedAmounts.get(0).add(remainder));

        List<ExpenseShare> shares = new ArrayList<>();
        List<ShareRequest> shareRequests = request.shares();
        for (int i = 0; i < shareRequests.size(); i++) {
            Participant participant = participantMap.get(shareRequests.get(i).participantId());
            shares.add(createShare(expense, participant, adjustedAmounts.get(i)));
        }
        return shares;
    }

    private ExpenseShare createShare(Expense expense, Participant participant, BigDecimal amount) {
        ExpenseShare share = new ExpenseShare();
        share.setExpense(expense);
        share.setParticipant(participant);
        share.setShareAmount(amount);
        return share;
    }

    private ExpenseResponse toExpenseResponse(Expense expense) {
        List<ShareResponse> shareResponses = expense.getShares().stream()
                .map(s -> new ShareResponse(
                        s.getParticipant().getId(),
                        s.getParticipant().getName(),
                        s.getShareAmount()))
                .toList();

        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getSplitType().name(),
                expense.getPaidBy().getId(),
                expense.getPaidBy().getName(),
                expense.getCreatedAt(),
                shareResponses
        );
    }
}
