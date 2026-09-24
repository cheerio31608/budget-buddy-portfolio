package com.finance.budget_buddy.repository;

import com.finance.budget_buddy.entity.AiReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {
    List<AiReport> findByUserIdOrderByGeneratedAtDesc(Long userId);
}
