package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.dto.transaction.TransactionCreateApiRequest;
import com.finance.budget_buddy.dto.transaction.TransactionResponse;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @Valid @RequestBody TransactionCreateApiRequest request) {
        return ResponseEntity.ok(TransactionResponse.from(
                transactionService.createTransaction(request.toCommand(principal.userId()))));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal) {
        List<TransactionResponse> transactions = transactionService.getTransactions(principal.userId()).stream()
                .map(TransactionResponse::from)
                .toList();
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal,
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(TransactionResponse.from(
                transactionService.getTransactionForUser(transactionId, principal.userId())));
    }
}
