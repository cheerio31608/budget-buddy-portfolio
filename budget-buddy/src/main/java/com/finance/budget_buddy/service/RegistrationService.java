package com.finance.budget_buddy.service;

import com.finance.budget_buddy.dto.auth.RegisterRequest;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.entity.User;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.CategoryRepository;
import com.finance.budget_buddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Creates an account and its own starter categories as one transaction. */
@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final PasswordEncoder passwords;

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        if (users.existsByEmailIgnoreCase(email)) throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        User user = users.saveAndFlush(User.builder().email(email)
                .passwordHash(passwords.encode(request.password())).build());
        categories.save(Category.builder().userId(user.getUserId()).name("급여").type(TransactionType.INCOME).build());
        for (String name : List.of("식비", "교통", "주거", "의료", "문화", "기타")) {
            categories.save(Category.builder().userId(user.getUserId()).name(name).type(TransactionType.EXPENSE).build());
        }
        return user;
    }

    @Transactional
    public void logout(Long userId) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).revokeTokens();
    }
}
