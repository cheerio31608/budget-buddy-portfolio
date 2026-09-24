package com.finance.budget_buddy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private long tokenVersion = 0;

    @Column(name = "demo_account", nullable = false)
    @Builder.Default
    private boolean demoAccount = false;

    public void revokeTokens() { tokenVersion++; }

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public void updateBalance(BigDecimal amount, TransactionType type) {
        if (TransactionType.INCOME == type) {
            this.balance = this.balance.add(amount);
        } else if (TransactionType.EXPENSE == type) {
            this.balance = this.balance.subtract(amount);
        }
    }
}
