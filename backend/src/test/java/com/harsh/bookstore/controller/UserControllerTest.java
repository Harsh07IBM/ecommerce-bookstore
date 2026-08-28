package com.harsh.bookstore.controller;

import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.GiftPointsResponse;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for UserController.
 */
@WebMvcTest(value = UserController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;


    // ==================================================================
    // HELPERS
    // ==================================================================

    private UsernamePasswordAuthenticationToken userAuth(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hash");
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private User freshUser(int balance) {
        User u = new User();
        u.setId(1L);
        u.setEmail("test@example.com");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setPasswordHash("hash");
        u.setGiftPoints(balance);
        return u;
    }


    // ==================================================================
    // TESTS
    // ==================================================================

    @Test
    void getGiftPoints_returns200() throws Exception {
        when(userRepository.findById(eq(1L))).thenReturn(Optional.of(freshUser(120)));

        mockMvc.perform(get("/api/users/me/gift-points")
                        .with(authentication(userAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftPoints").value(120));
    }

    @Test
    void getGiftPoints_returns401_noJwt() throws Exception {
        mockMvc.perform(get("/api/users/me/gift-points"))
                .andExpect(status().isUnauthorized());
    }
}
