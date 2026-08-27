package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.CategoryDto;
import com.harsh.bookstore.service.CategoryService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer tests for CategoryController.
 * @WebMvcTest loads the controller + GlobalExceptionHandler (no DB, no service).
 */
@WebMvcTest(value = CategoryController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(com.harsh.bookstore.config.SecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    // JwtAuthFilter is a @Component and gets picked up by @WebMvcTest.
    // It needs JwtService + UserRepository to construct — we mock them here.
    @MockBean
    private com.harsh.bookstore.service.JwtService jwtService;

    @MockBean
    private com.harsh.bookstore.repository.UserRepository userRepository;

    @Test
    void listCategories_returns200_withJsonArray() throws Exception {
        CategoryDto fiction = dto(1L, "Fiction", "fiction", 15);
        CategoryDto science = dto(2L, "Science", "science", 8);
        when(categoryService.listAllCategories()).thenReturn(List.of(fiction, science));

        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Fiction"))
            .andExpect(jsonPath("$[0].slug").value("fiction"))
            .andExpect(jsonPath("$[0].bookCount").value(15))
            .andExpect(jsonPath("$[1].name").value("Science"))
            .andExpect(jsonPath("$[1].bookCount").value(8));
    }

    @Test
    void listCategories_returns200_withEmptyArray_whenNoCategories() throws Exception {
        when(categoryService.listAllCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    // --- helpers ---

    private CategoryDto dto(Long id, String name, String slug, long bookCount) {
        CategoryDto dto = new CategoryDto();
        dto.setId(id);
        dto.setName(name);
        dto.setSlug(slug);
        dto.setBookCount(bookCount);
        return dto;
    }
}
