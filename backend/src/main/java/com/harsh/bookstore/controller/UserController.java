package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.GiftPointsResponse;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * UserController — handles user-profile endpoints (FEAT-09+).
 *
 * AUTH:
 *   All endpoints require a valid JWT. Spring Security rejects unauthenticated
 *   requests before any method is reached (401 via HttpStatusEntryPoint).
 *
 * GIFT POINTS FRESHNESS (design D-08):
 *   The JWT principal was loaded at authentication time. We reload the User from
 *   the DB on every request to guarantee the latest balance is returned.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    /**
     * Return the authenticated user's current gift point balance.
     *
     * @param authentication Spring Security principal — always non-null (JWT required)
     * @return 200 with { "giftPoints": N }
     */
    @GetMapping("/me/gift-points")
    public GiftPointsResponse getGiftPoints(Authentication authentication) {
        User principal = (User) authentication.getPrincipal();
        User fresh = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        GiftPointsResponse response = new GiftPointsResponse();
        response.setGiftPoints(fresh.getGiftPoints());
        return response;
    }
}
