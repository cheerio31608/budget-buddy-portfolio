package com.finance.budget_buddy.controller;

import com.finance.budget_buddy.dto.category.CategoryResponse;
import com.finance.budget_buddy.security.BudgetBuddyPrincipal;
import com.finance.budget_buddy.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> categories(
            @AuthenticationPrincipal BudgetBuddyPrincipal principal) {
        return ResponseEntity.ok(categoryService.getCategories(principal.userId()));
    }
}
