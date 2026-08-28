package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.BookDto;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.service.RecommendationService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * RecommendationController — GET /api/recommendations (FEAT-14).
 *
 * AUTH:
 *   Requires a valid JWT. Spring Security enforces this via the existing
 *   anyRequest().authenticated() rule — unauthenticated requests receive 401
 *   before this method is reached. The Authentication parameter is therefore
 *   guaranteed non-null.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }


    /**
     * Return up to 6 personalised book recommendations for the authenticated user.
     * Results are drawn from categories the user has previously purchased from,
     * excluding books already ordered. Sorted by title ascending.
     *
     * @param authentication Spring Security principal — guaranteed non-null (JWT required)
     * @return list of up to 6 BookDto; empty array if no past orders
     */
    @GetMapping
    public List<BookDto> getRecommendations(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return recommendationService.getRecommendations(user.getId());
    }
}
