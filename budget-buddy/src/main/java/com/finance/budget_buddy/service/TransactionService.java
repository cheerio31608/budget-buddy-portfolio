package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.Transaction;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.entity.User;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.CategoryRepository;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository,
                              UserRepository userRepository,
                              CategoryRepository categoryRepository,
                              Clock clock) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    /**
     * Creates a transaction and updates the user's balance in one DB transaction.
     * Pessimistic locking serializes concurrent payments for the same user.
     */
    @Transactional
    public Transaction createTransaction(TransactionCreateRequest request) {
        validateRequest(request);

        User user = userRepository.findByIdForUpdate(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            Transaction existing = transactionRepository
                    .findByUserIdAndIdempotencyKey(user.getUserId(), idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                if (!hasSamePayload(existing, request)) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                return existing;
            }
        }

        return createNewTransaction(request, user, idempotencyKey);
    }

    private Transaction createNewTransaction(TransactionCreateRequest request, User user, String idempotencyKey) {
        Category category = categoryRepository.findByCategoryIdAndUserId(request.categoryId(), user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (category.getType() != request.transactionType()) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_MISMATCH);
        }

        if (TransactionType.EXPENSE == request.transactionType()
                && user.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        BigDecimal balanceBefore = user.getBalance();
        BigDecimal balanceAfter = TransactionType.INCOME == request.transactionType()
                ? balanceBefore.add(request.amount())
                : balanceBefore.subtract(request.amount());
        if (balanceAfter.compareTo(MAX_BALANCE) > 0) {
            throw new BusinessException(ErrorCode.BALANCE_LIMIT_EXCEEDED,
                    "등록 후 잔액이 지원 범위를 초과합니다.");
        }
        user.updateBalance(request.amount(), request.transactionType());

        Transaction transaction = Transaction.builder()
                .userId(user.getUserId())
                .categoryId(category.getCategoryId())
                .amount(request.amount())
                .balanceBefore(balanceBefore)
                .balanceAfter(user.getBalance())
                .transactionType(request.transactionType())
                .description(request.description())
                .vendorName(request.vendorName())
                .location(request.location())
                .transactionAt(request.transactionAt().truncatedTo(ChronoUnit.MICROS))
                .idempotencyKey(idempotencyKey)
                .build();

        return transactionRepository.save(transaction);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "거래 금액은 0보다 커야 합니다.");
        }

        int fractionDigits = Math.max(amount.scale(), 0);
        int integerDigits = Math.max(amount.precision() - amount.scale(), 0);
        if (fractionDigits > 2 || integerDigits > 13) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 금액은 정수 13자리와 소수 2자리까지 입력할 수 있습니다.");
        }
    }

    private void validateRequest(TransactionCreateRequest request) {
        if (request == null
                || request.userId() == null || request.userId() <= 0
                || request.categoryId() == null || request.categoryId() <= 0
                || request.transactionType() == null
                || request.transactionAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래의 필수 입력값을 확인해 주세요.");
        }
        validateAmount(request.amount());
        if (request.description() != null && request.description().length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 메모는 1000자 이하여야 합니다.");
        }
        if (request.transactionAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "미래 시점의 거래는 등록할 수 없습니다.");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }

    private boolean hasSamePayload(Transaction existing, TransactionCreateRequest request) {
        return Objects.equals(existing.getCategoryId(), request.categoryId())
                && existing.getAmount().compareTo(request.amount()) == 0
                && existing.getTransactionType() == request.transactionType()
                && Objects.equals(existing.getDescription(), request.description())
                && Objects.equals(existing.getVendorName(), request.vendorName())
                && Objects.equals(existing.getLocation(), request.location())
                && sameTimestamp(existing.getTransactionAt(), request.transactionAt());
    }

    private boolean sameTimestamp(LocalDateTime existing, LocalDateTime requested) {
        if (existing == null || requested == null) {
            return existing == requested;
        }
        // PostgreSQL stores timestamp values with microsecond precision.
        return existing.truncatedTo(ChronoUnit.MICROS)
                .equals(requested.truncatedTo(ChronoUnit.MICROS));
    }

    public List<Transaction> getTransactions(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Snapshots describe ledger posting order, while transactionAt is the event time.
        return transactionRepository.findByUserIdOrderByTransactionIdDesc(userId);
    }

    public Transaction getTransactionForUser(Long transactionId, Long userId) {
        return transactionRepository.findByTransactionIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }
}
