package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.CategoryDto;
import com.harsh.bookstore.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CategoryController — HTTP entry point for category listing.
 *
 * Exposes one endpoint:
 *   GET /api/categories — returns all categories alphabetically with book counts.
 *
 * No authentication required (guest access per spec FR-08).
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * GET /api/categories
     * Returns a JSON array of all categories, ordered alphabetically by name,
     * each including id, name, slug, and bookCount.
     */
    @GetMapping
    public List<CategoryDto> listCategories() {
        return categoryService.listAllCategories();
    }
}
