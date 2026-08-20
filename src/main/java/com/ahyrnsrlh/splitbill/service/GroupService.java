package com.ahyrnsrlh.splitbill.service;

import com.ahyrnsrlh.splitbill.dto.request.CreateGroupRequest;
import com.ahyrnsrlh.splitbill.dto.response.ExpenseResponse;
import com.ahyrnsrlh.splitbill.dto.response.GroupResponse;
import com.ahyrnsrlh.splitbill.dto.response.ParticipantResponse;
import com.ahyrnsrlh.splitbill.dto.response.ShareResponse;
import com.ahyrnsrlh.splitbill.entity.BillGroup;
import com.ahyrnsrlh.splitbill.entity.Expense;
import com.ahyrnsrlh.splitbill.entity.ExpenseShare;
import com.ahyrnsrlh.splitbill.entity.Participant;
import com.ahyrnsrlh.splitbill.exception.BusinessException;
import com.ahyrnsrlh.splitbill.exception.ResourceNotFoundException;
import com.ahyrnsrlh.splitbill.repository.BillGroupRepository;
import com.ahyrnsrlh.splitbill.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final BillGroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;

    public GroupService(BillGroupRepository groupRepository, ExpenseRepository expenseRepository) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        validateUniqueParticipantNames(request.participants().stream()
                .map(p -> p.name().toLowerCase())
                .toList());

        BillGroup group = new BillGroup();
        group.setName(request.name());

        request.participants().forEach(pr -> {
            Participant participant = new Participant();
            participant.setName(pr.name());
            participant.setGroup(group);
            group.getParticipants().add(participant);
        });

        BillGroup saved = groupRepository.save(group);
        return toGroupResponse(saved, List.of());
    }

    public GroupResponse getGroup(Long groupId) {
        BillGroup group = findGroupById(groupId);
        List<Expense> expenses = expenseRepository.findByGroupIdWithPayer(groupId);
        return toGroupResponse(group, expenses);
    }

    public BillGroup findGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));
    }

    private void validateUniqueParticipantNames(List<String> names) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                throw new BusinessException("Duplicate participant name in request: " + name);
            }
        }
    }

    private GroupResponse toGroupResponse(BillGroup group, List<Expense> expenses) {
        List<ParticipantResponse> participantResponses = group.getParticipants().stream()
                .map(p -> new ParticipantResponse(p.getId(), p.getName(), p.getCreatedAt()))
                .toList();

        List<ExpenseResponse> expenseResponses = expenses.stream()
                .map(this::toExpenseResponse)
                .toList();

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getCreatedAt(),
                participantResponses,
                expenseResponses
        );
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
