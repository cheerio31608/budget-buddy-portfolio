package com.finance.budget_buddy.service;

import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.AiUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/** Reserves an attempt before the external call; failed calls also consume quota. */
@Service
public class AiQuotaService {
    private final AiUsageRepository usage;
    private final Clock clock;
    private final int userLimit;
    private final int globalLimit;
    private final long cooldown;
    public AiQuotaService(AiUsageRepository usage, Clock clock,
                          @Value("${app.ai.daily-user-limit:3}") int userLimit,
                          @Value("${app.ai.daily-global-limit:30}") int globalLimit,
                          @Value("${app.ai.cooldown-seconds:60}") long cooldown) {
        this.usage = usage; this.clock = clock; this.userLimit = userLimit;
        this.globalLimit = globalLimit; this.cooldown = cooldown;
        if (userLimit < 0 || globalLimit < 0 || cooldown < 0) throw new IllegalArgumentException("AI limits must be nonnegative");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(Long userId) {
        var global = usage.lockGlobal();
        var user = usage.findUser(userId);
        var now = LocalDateTime.now(clock);
        var today = now.toLocalDate();
        int globalCount = today.equals(global.day()) ? global.count() : 0;
        int userCount = user != null && today.equals(user.day()) ? user.count() : 0;
        if (globalCount >= globalLimit || userCount >= userLimit || (user != null && user.lastRequestedAt() != null
                && now.isBefore(user.lastRequestedAt().plusSeconds(cooldown)))) {
            throw new BusinessException(ErrorCode.AI_LIMIT_EXCEEDED);
        }
        usage.save("global", today, globalCount + 1, now);
        usage.save("user:" + userId, today, userCount + 1, now);
    }
}
