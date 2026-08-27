package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.CategoryDto;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.exception.CategoryNotFoundException;
import com.harsh.bookstore.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CategoryService — business logic for category operations.
 *
 * listAllCategories():
 *   Queries all categories with their book counts via a single JOIN query,
 *   maps to CategoryDto, and returns them alphabetically ordered.
 *
 * getCategoryBySlug(String slug):
 *   Looks up a Category by URL slug (case-insensitive).
 *   Used by BookService.listBooksByCategory() to resolve the slug to an entity.
 *   Throws CategoryNotFoundException if the slug has no match.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Returns all categories alphabetically, each with its live book count.
     */
    public List<CategoryDto> listAllCategories() {
        List<Object[]> rows = categoryRepository.findAllWithBookCount();
        return rows.stream()
                   .map(row -> toDto((Category) row[0], (Long) row[1]))
                   .toList();
    }

    /**
     * Resolves a slug to a Category entity.
     * @throws CategoryNotFoundException if no category has that slug.
     */
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlugIgnoreCase(slug)
               .orElseThrow(() -> new CategoryNotFoundException(slug));
    }

    // --- private helpers ---

    private CategoryDto toDto(Category category, Long bookCount) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        dto.setBookCount(bookCount);
        return dto;
    }
}
