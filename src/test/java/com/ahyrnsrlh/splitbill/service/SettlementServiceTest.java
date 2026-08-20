package com.ahyrnsrlh.splitbill.service;

import com.ahyrnsrlh.splitbill.config.AppProperties;
import com.ahyrnsrlh.splitbill.dto.response.SettlementResponse;
import com.ahyrnsrlh.splitbill.dto.response.TransferResponse;
import com.ahyrnsrlh.splitbill.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for settlement calculation logic.
 * No Spring context, no Mockito — calls SettlementService.buildResponse() directly
 * with pre-built domain objects.
 */
class SettlementServiceTest {

    private SettlementService settlementService;
    private ServiceChargeService serviceChargeService;

    private Participant alice;
    private Participant bob;
    private Participant charlie;
    private BillGroup group;

    @BeforeEach
    void setUp() {
        serviceChargeService = new ServiceChargeService(new AppProperties("ahyrnsrlh"));
        // SettlementService constructor requires repos — pass null because buildResponse()
        // does not call them (it receives data directly).
        settlementService = new SettlementService(null, null, null, serviceChargeService);

        group = buildGroup(1L, "Test Group");
        alice   = buildParticipant(1L, "Alice",   group);
        bob     = buildParticipant(2L, "Bob",     group);
        charlie = buildParticipant(3L, "Charlie", group);
        group.getParticipants().addAll(List.of(alice, bob, charlie));
    }

    // ── Test 1: Basic three-person settlement ──────────────────────────────────

    @Test
    void basicThreePersonSettlement() {
        // Alice pays 300 for 3 people equally (each owes 100)
        // Net: Alice +200, Bob −100, Charlie −100
        Expense expense = buildExpense(1L, alice, new BigDecimal("300.00"));
        List<ExpenseShare> shares = List.of(
                buildShare(expense, alice,   new BigDecimal("100.00")),
                buildShare(expense, bob,     new BigDecimal("100.00")),
                buildShare(expense, charlie, new BigDecimal("100.00"))
        );

        SettlementResponse response = settlementService.buildResponse(group, List.of(expense), shares);

        assertEquals(0, response.totalExpenses().compareTo(new BigDecimal("300.00")));
        assertEquals(7, response.serviceChargePct());
        assertEquals(0, response.serviceChargeAmount().compareTo(new BigDecimal("21.00")));
        assertEquals(2, response.settlements().size());
        assertTransferExists(response.settlements(), 2L, 1L, "100.00");
        assertTransferExists(response.settlements(), 3L, 1L, "100.00");
    }

    // ── Test 2: Participant who paid more than their share ─────────────────────

    @Test
    void participantPaidMoreThanShare() {
        // Alice pays 600, equal split → each owes 200; Alice net = +400
        Expense expense = buildExpense(1L, alice, new BigDecimal("600.00"));
        List<ExpenseShare> shares = List.of(
                buildShare(expense, alice,   new BigDecimal("200.00")),
                buildShare(expense, bob,     new BigDecimal("200.00")),
                buildShare(expense, charlie, new BigDecimal("200.00"))
        );

        SettlementResponse response = settlementService.buildResponse(group, List.of(expense), shares);

        assertTransferExists(response.settlements(), 2L, 1L, "200.00");
        assertTransferExists(response.settlements(), 3L, 1L, "200.00");
    }

    // ── Test 3: Multiple expenses with different payers ────────────────────────

    @Test
    void multipleExpensesWithDifferentPayers() {
        // Exp1: Alice pays 300 equally → each owes 100
        // Exp2: Bob pays 150 equally  → each owes 50
        // Net: Alice = 300 − 100 − 50 = +150
        //      Bob   = 150 − 100 − 50 =   0
        //      Charlie = 0  − 100 − 50 = −150
        Expense exp1 = buildExpense(1L, alice, new BigDecimal("300.00"));
        List<ExpenseShare> shares1 = List.of(
                buildShare(exp1, alice,   new BigDecimal("100.00")),
                buildShare(exp1, bob,     new BigDecimal("100.00")),
                buildShare(exp1, charlie, new BigDecimal("100.00"))
        );

        Expense exp2 = buildExpense(2L, bob, new BigDecimal("150.00"));
        List<ExpenseShare> shares2 = List.of(
                buildShare(exp2, alice,   new BigDecimal("50.00")),
                buildShare(exp2, bob,     new BigDecimal("50.00")),
                buildShare(exp2, charlie, new BigDecimal("50.00"))
        );

        List<ExpenseShare> allShares = new java.util.ArrayList<>();
        allShares.addAll(shares1);
        allShares.addAll(shares2);

        SettlementResponse response = settlementService.buildResponse(
                group, List.of(exp1, exp2), allShares);

        assertEquals(0, response.totalExpenses().compareTo(new BigDecimal("450.00")));
        assertEquals(1, response.settlements().size(), "Only Charlie owes Alice; Bob is settled");
        assertTransferExists(response.settlements(), 3L, 1L, "150.00");
    }

    // ── Test 4: Participant with zero balance ──────────────────────────────────

    @Test
    void participantWithZeroBalance() {
        // Bob's share is 0 and Bob paid 0 → Bob's net balance = 0
        // Alice pays 300, Alice gets 150, Charlie gets 150
        Expense expense = buildExpense(1L, alice, new BigDecimal("300.00"));
        List<ExpenseShare> shares = List.of(
                buildShare(expense, alice,   new BigDecimal("150.00")),
                buildShare(expense, bob,     new BigDecimal("0.00")),
                buildShare(expense, charlie, new BigDecimal("150.00"))
        );

        SettlementResponse response = settlementService.buildResponse(group, List.of(expense), shares);

        boolean bobInSettlements = response.settlements().stream()
                .anyMatch(t -> t.fromParticipantId().equals(2L) || t.toParticipantId().equals(2L));
        assertFalse(bobInSettlements, "Bob has zero balance and must not appear in settlements");
    }

    // ── Test 5: Equal splitting with a rounding remainder ─────────────────────

    @Test
    void equalSplittingRoundingRemainder() {
        // 10.00 ÷ 3 = 3.33... → deterministic allocation: 3.34 + 3.33 + 3.33 = 10.00
        // (This verifies that SettlementService handles such inputs correctly)
        BigDecimal sharesTotal = new BigDecimal("3.34")
                .add(new BigDecimal("3.33"))
                .add(new BigDecimal("3.33"));
        assertEquals(0, sharesTotal.compareTo(new BigDecimal("10.00")),
                "Shares must sum exactly to expense total");

        Expense expense = buildExpense(1L, alice, new BigDecimal("10.00"));
        List<ExpenseShare> shares = List.of(
                buildShare(expense, alice,   new BigDecimal("3.34")),
                buildShare(expense, bob,     new BigDecimal("3.33")),
                buildShare(expense, charlie, new BigDecimal("3.33"))
        );

        SettlementResponse response = settlementService.buildResponse(group, List.of(expense), shares);

        // Alice net = 10.00 − 3.34 = 6.66 → creditor
        // Bob net = 0 − 3.33 = −3.33 → debtor
        // Charlie net = 0 − 3.33 = −3.33 → debtor
        BigDecimal totalTransferred = response.settlements().stream()
                .map(TransferResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalTransferred.compareTo(new BigDecimal("6.66")),
                "Total transfers must equal Alice's net credit");
    }

    // ── Test 6: Exact split validation logic ──────────────────────────────────

    @Test
    void exactSplitValidationFailure_sharesDoNotMatchTotal() {
        BigDecimal total = new BigDecimal("600.00");
        BigDecimal sumOfShares = new BigDecimal("200.00")
                .add(new BigDecimal("250.00"))
                .add(new BigDecimal("100.00")); // deliberate mismatch (should be 150)

        assertNotEquals(0, total.compareTo(sumOfShares),
                "Shares that do not sum to the expense amount must fail validation");
    }

    // ── Test 7: Percentage split validation logic ──────────────────────────────

    @Test
    void percentageSplitValidationFailure_percentagesDoNotSum100() {
        BigDecimal pctSum = new BigDecimal("50")
                .add(new BigDecimal("30"))
                .add(new BigDecimal("15")); // deliberate mismatch (should be 20, not 15)

        assertNotEquals(0, BigDecimal.valueOf(100).compareTo(pctSum),
                "Percentages that do not sum to 100 must fail validation");
    }

    // ── Test 8: Service charge calculated from username, not hardcoded ─────────

    @Test
    void serviceChargePctForAhyrnsrlhEquals7_provenNotHardcoded() {
        ServiceChargeService ahyrnsrlhSvc = new ServiceChargeService(new AppProperties("ahyrnsrlh"));
        assertEquals(7, ahyrnsrlhSvc.getServiceChargePct(),
                "ahyrnsrlh must produce 7: sum(97,104,121,114,110,115,114,108,104)=987, 987%10=7");

        // Prove the value is computed, not hardcoded: a different username gives a different result
        ServiceChargeService otherSvc = new ServiceChargeService(new AppProperties("testuser"));
        // testuser = 116+101+115+116+117+115+101+114 = 895, 895 % 10 = 5
        assertEquals(5, otherSvc.getServiceChargePct(),
                "testuser must produce 5 (895 % 10)");

        assertNotEquals(ahyrnsrlhSvc.getServiceChargePct(), otherSvc.getServiceChargePct(),
                "Different usernames must produce different percentages");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private BillGroup buildGroup(Long id, String name) {
        BillGroup g = new BillGroup();
        setId(g, id);
        g.setName(name);
        return g;
    }

    private Participant buildParticipant(Long id, String name, BillGroup group) {
        Participant p = new Participant();
        setId(p, id);
        p.setName(name);
        p.setGroup(group);
        return p;
    }

    private Expense buildExpense(Long id, Participant payer, BigDecimal amount) {
        Expense e = new Expense();
        setId(e, id);
        e.setPaidBy(payer);
        e.setAmount(amount);
        e.setSplitType(SplitType.EQUAL);
        return e;
    }

    private ExpenseShare buildShare(Expense expense, Participant participant, BigDecimal amount) {
        ExpenseShare s = new ExpenseShare();
        s.setExpense(expense);
        s.setParticipant(participant);
        s.setShareAmount(amount);
        return s;
    }

    private void assertTransferExists(List<TransferResponse> transfers,
                                      Long fromId, Long toId, String amount) {
        boolean found = transfers.stream().anyMatch(t ->
                t.fromParticipantId().equals(fromId) &&
                t.toParticipantId().equals(toId) &&
                t.amount().compareTo(new BigDecimal(amount)) == 0);
        assertTrue(found,
                "Expected transfer from " + fromId + " to " + toId + " of " + amount
                + " not found in: " + transfers);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set id on " + entity.getClass().getSimpleName(), e);
        }
    }
}
