package com.finance.budget_buddy.service;

import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.repository.AiUsageRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuotaServiceTest {
    private final AiUsageRepository repository = mock(AiUsageRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    @Test void globalLimitBlocksNewAccountsToo() {
        when(repository.lockGlobal()).thenReturn(new AiUsageRepository.Usage(now.toLocalDate(), 30, now));
        assertThatThrownBy(() -> new AiQuotaService(repository, clock, 3, 30, 60).reserve(1L)).isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any(), any(), anyInt(), any());
    }
    @Test void cooldownBlocksAnImmediateRetry() {
        when(repository.lockGlobal()).thenReturn(new AiUsageRepository.Usage(now.toLocalDate(), 1, now));
        when(repository.findUser(1L)).thenReturn(new AiUsageRepository.Usage(now.toLocalDate(), 1, now.minusSeconds(59)));
        assertThatThrownBy(() -> new AiQuotaService(repository, clock, 3, 30, 60).reserve(1L)).isInstanceOf(BusinessException.class);
    }
    @Test void newDayResetsCounts() {
        when(repository.lockGlobal()).thenReturn(new AiUsageRepository.Usage(now.toLocalDate().minusDays(1), 30, now.minusDays(1)));
        when(repository.findUser(1L)).thenReturn(new AiUsageRepository.Usage(now.toLocalDate().minusDays(1), 3, now.minusDays(1)));
        new AiQuotaService(repository, clock, 3, 30, 60).reserve(1L);
        verify(repository).save("global", now.toLocalDate(), 1, now);
        verify(repository).save("user:1", now.toLocalDate(), 1, now);
    }
}
