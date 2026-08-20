package com.ahyrnsrlh.splitbill.controller;

import com.ahyrnsrlh.splitbill.dto.request.CreateExpenseRequest;
import com.ahyrnsrlh.splitbill.dto.response.ExpenseResponse;
import com.ahyrnsrlh.splitbill.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/expenses")
@Tag(name = "Expenses", description = "Expense management within a group")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @Operation(summary = "Add an expense to a group")
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.createExpense(groupId, request));
    }

    @GetMapping
    @Operation(summary = "List all expenses in a group")
    public ResponseEntity<List<ExpenseResponse>> getExpenses(@PathVariable Long groupId) {
        return ResponseEntity.ok(expenseService.getExpenses(groupId));
    }
}
