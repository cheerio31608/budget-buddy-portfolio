package com.finance.budget_buddy.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class AiUsageRepository {
    private final JdbcTemplate jdbc;
    public record Usage(LocalDate day, int count, LocalDateTime lastRequestedAt) {}
    public Usage lockGlobal() {
        return jdbc.queryForObject("SELECT usage_day, request_count, last_requested_at FROM ai_usage WHERE usage_key = 'global' FOR UPDATE",
                (rs, row) -> new Usage(rs.getObject(1, LocalDate.class), rs.getInt(2), rs.getObject(3, LocalDateTime.class)));
    }
    public Usage findUser(Long userId) {
        var rows = jdbc.query("SELECT usage_day, request_count, last_requested_at FROM ai_usage WHERE usage_key = ?",
                (rs, row) -> new Usage(rs.getObject(1, LocalDate.class), rs.getInt(2), rs.getObject(3, LocalDateTime.class)), "user:" + userId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void save(String key, LocalDate day, int count, LocalDateTime now) {
        int changed = jdbc.update("UPDATE ai_usage SET usage_day = ?, request_count = ?, last_requested_at = ? WHERE usage_key = ?",
                day, count, now, key);
        if (changed == 0) jdbc.update("INSERT INTO ai_usage (usage_key, usage_day, request_count, last_requested_at) VALUES (?, ?, ?, ?)",
                key, day, count, now);
    }
}
