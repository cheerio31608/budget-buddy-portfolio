package com.finance.budget_buddy.dto.category;

import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.entity.TransactionType;

public record CategoryResponse(Long categoryId, String name, TransactionType type) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getCategoryId(), category.getName(), category.getType());
    }
}
