package com.finance.budget_buddy.repository;

import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsByUserId(Long userId);
    List<Transaction> findByUserIdAndTransactionType(Long userId, TransactionType transactionType);
    List<Transaction> findByUserIdOrderByTransactionIdDesc(Long userId);
    List<Transaction> findByUserIdAndTransactionAtGreaterThanEqualAndTransactionAtLessThanOrderByTransactionAtDesc(
            Long userId, LocalDateTime startInclusive, LocalDateTime endExclusive);
    Optional<Transaction> findByTransactionIdAndUserId(Long transactionId, Long userId);
    Optional<Transaction> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
