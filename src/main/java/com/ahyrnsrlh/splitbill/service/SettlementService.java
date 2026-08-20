package com.ahyrnsrlh.splitbill.service;

import com.ahyrnsrlh.splitbill.dto.response.SettlementResponse;
import com.ahyrnsrlh.splitbill.dto.response.TransferResponse;
import com.ahyrnsrlh.splitbill.entity.BillGroup;
import com.ahyrnsrlh.splitbill.entity.Expense;
import com.ahyrnsrlh.splitbill.entity.ExpenseShare;
import com.ahyrnsrlh.splitbill.entity.Participant;
import com.ahyrnsrlh.splitbill.repository.ExpenseRepository;
import com.ahyrnsrlh.splitbill.repository.ExpenseShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Computes settlements for a bill group using a greedy debtor-creditor matching algorithm.
 *
 * Net balance per participant = total amount paid - total share allocated.
 *   Positive balance → participant should receive money.
 *   Negative balance → participant owes money.
 *
 * The greedy algorithm pairs the largest debtor with the largest creditor on each iteration,
 * producing at most n-1 transfers for typical balanced cases. It does not guarantee the global
 * minimum number of transactions in all edge cases.
 *
 * The service charge is informational only. It is not added to participant balances.
 */
@Service
@Transactional(readOnly = true)
public class SettlementService {

    static final int MONETARY_SCALE = 2;
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final ServiceChargeService serviceChargeService;

    public SettlementService(GroupService groupService,
                             ExpenseRepository expenseRepository,
                             ExpenseShareRepository expenseShareRepository,
                             ServiceChargeService serviceChargeService) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.serviceChargeService = serviceChargeService;
    }

    public SettlementResponse calculateSettlement(Long groupId) {
        BillGroup group = groupService.findGroupById(groupId);
        List<Expense> expenses = expenseRepository.findByGroupIdWithPayer(groupId);
        List<ExpenseShare> allShares = expenseShareRepository.findByGroupIdWithParticipant(groupId);
        return buildResponse(group, expenses, allShares);
    }

    /**
     * Package-visible for unit testing without Spring context or mocking.
     * Accepts pre-loaded data so tests can call this directly.
     */
    SettlementResponse buildResponse(BillGroup group,
                                     List<Expense> expenses,
                                     List<ExpenseShare> allShares) {
        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

        Map<Long, BigDecimal> balances = computeBalances(group.getParticipants(), expenses, allShares);
        List<TransferResponse> transfers = resolveTransfers(balances, group.getParticipants());

        int pct = serviceChargeService.getServiceChargePct();
        BigDecimal chargeAmount = serviceChargeService.calculateServiceChargeAmount(totalExpenses);

        return new SettlementResponse(
                group.getId(),
                group.getName(),
                totalExpenses,
                pct,
                chargeAmount,
                transfers
        );
    }

    private Map<Long, BigDecimal> computeBalances(List<Participant> participants,
                                                   List<Expense> expenses,
                                                   List<ExpenseShare> allShares) {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (Participant p : participants) {
            balances.put(p.getId(), ZERO);
        }

        // Credit participants for what they paid
        for (Expense expense : expenses) {
            Long payerId = expense.getPaidBy().getId();
            balances.merge(payerId, expense.getAmount(), BigDecimal::add);
        }

        // Debit participants for their allocated shares
        for (ExpenseShare share : allShares) {
            Long participantId = share.getParticipant().getId();
            balances.merge(participantId, share.getShareAmount().negate(), BigDecimal::add);
        }

        return balances;
    }

    /**
     * Greedy debtor-creditor algorithm.
     * At each step pairs the participant who owes the most with the one owed the most,
     * records a transfer for the lesser of the two amounts, and updates both balances.
     * Continues until all balances are settled (within rounding tolerance).
     */
    private List<TransferResponse> resolveTransfers(Map<Long, BigDecimal> balances,
                                                     List<Participant> participants) {
        Map<Long, String> nameById = new HashMap<>();
        for (Participant p : participants) {
            nameById.put(p.getId(), p.getName());
        }

        // Work in integer units (cents) to avoid cumulative BigDecimal rounding issues
        List<long[]> creditors = new ArrayList<>();
        List<long[]> debtors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            BigDecimal scaled = entry.getValue().setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
            long units = scaled.unscaledValue().longValueExact();
            if (units > 0) {
                creditors.add(new long[]{entry.getKey(), units});
            } else if (units < 0) {
                debtors.add(new long[]{entry.getKey(), -units}); // store as positive
            }
        }

        List<TransferResponse> transfers = new ArrayList<>();
        int di = 0, ci = 0;

        while (di < debtors.size() && ci < creditors.size()) {
            long[] debtor = debtors.get(di);
            long[] creditor = creditors.get(ci);

            long amount = Math.min(debtor[1], creditor[1]);
            if (amount > 0) {
                BigDecimal transferAmount = BigDecimal.valueOf(amount)
                        .scaleByPowerOfTen(-MONETARY_SCALE)
                        .setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

                transfers.add(new TransferResponse(
                        debtor[0], nameById.get(debtor[0]),
                        creditor[0], nameById.get(creditor[0]),
                        transferAmount
                ));
            }

            debtor[1] -= amount;
            creditor[1] -= amount;

            if (debtor[1] == 0) di++;
            if (creditor[1] == 0) ci++;
        }

        return transfers;
    }
}
