package com.finance.budget_buddy.repository;

import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryIdAndUserId(Long categoryId, Long userId);

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findFirstByUserIdAndTypeOrderByCategoryIdAsc(Long userId, TransactionType type);
}
