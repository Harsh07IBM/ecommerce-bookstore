package com.harsh.bookstore.controller;

import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.JwtService;
import com.harsh.bookstore.service.RecommendationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for RecommendationController (FEAT-14).
 */
@WebMvcTest(value = RecommendationController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;


    private UsernamePasswordAuthenticationToken userAuth() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hash");
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private BookDto bookDto() {
        BookDto dto = new BookDto();
        dto.setId(5L);
        dto.setIsbn("9780132350884");
        dto.setTitle("Clean Code");
        dto.setAuthors(List.of("Robert C. Martin"));
        dto.setDescription("...");
        dto.setCoverImageUrl("https://example.com/cover.jpg");
        dto.setLanguage("en");
        dto.setCategory("Technology");
        dto.setPrice(new BigDecimal("599.00"));
        dto.setAvailability("IN_STOCK");
        return dto;
    }


    // ==================================================================
    // GET /api/recommendations
    // ==================================================================

    @Test
    void getRecommendations_returns200_authenticated() throws Exception {
        when(recommendationService.getRecommendations(eq(1L))).thenReturn(List.of(bookDto()));

        mockMvc.perform(get("/api/recommendations")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].title").value("Clean Code"))
                .andExpect(jsonPath("$[0].category").value("Technology"));
    }

    @Test
    void getRecommendations_returns401_noJwt() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isUnauthorized());
    }
}
