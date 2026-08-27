package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.CategoryDto;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.exception.CategoryNotFoundException;
import com.harsh.bookstore.repository.CategoryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CategoryService — Mockito only, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    // --- listAllCategories ---

    @Test
    void listAllCategories_returnsDtosAlphabetically() {
        Category fiction = category(1L, "Fiction", "fiction");
        Category science = category(2L, "Science", "science");

        // findAllWithBookCount returns rows ordered by name ASC
        when(categoryRepository.findAllWithBookCount()).thenReturn(
            List.of(new Object[]{fiction, 15L}, new Object[]{science, 8L})
        );

        List<CategoryDto> result = categoryService.listAllCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSlug()).isEqualTo("fiction");
        assertThat(result.get(0).getBookCount()).isEqualTo(15L);
        assertThat(result.get(1).getSlug()).isEqualTo("science");
        assertThat(result.get(1).getBookCount()).isEqualTo(8L);
    }

    // --- getCategoryBySlug ---

    @Test
    void getCategoryBySlug_returnsCategory_whenFound() {
        Category fiction = category(1L, "Fiction", "fiction");
        when(categoryRepository.findBySlugIgnoreCase("fiction"))
            .thenReturn(Optional.of(fiction));

        Category result = categoryService.getCategoryBySlug("fiction");

        assertThat(result.getName()).isEqualTo("Fiction");
    }

    @Test
    void getCategoryBySlug_throwsCategoryNotFoundException_whenNotFound() {
        when(categoryRepository.findBySlugIgnoreCase("nope"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryBySlug("nope"))
            .isInstanceOf(CategoryNotFoundException.class)
            .hasMessageContaining("nope");
    }

    // --- helpers ---

    private Category category(Long id, String name, String slug) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setSlug(slug);
        return c;
    }
}
