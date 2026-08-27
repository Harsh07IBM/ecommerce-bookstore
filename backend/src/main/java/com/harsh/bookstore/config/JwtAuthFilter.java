package com.harsh.bookstore.config;

import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


/**
 * JwtAuthFilter — reads the JWT from every incoming request and, if valid,
 * sets the authenticated user in Spring Security's context.
 *
 * WHAT IS A FILTER (in plain English):
 *   Before a request reaches any controller, it passes through a chain of
 *   "filters" — pieces of code that can inspect or modify it. Think of
 *   airport security: your luggage (the request) goes through scanners
 *   (filters) before you (the controller) receive it.
 *
 *   Spring Security is itself a filter chain. We insert our JWT filter into
 *   that chain so it runs on every request, before Spring Security makes any
 *   allow/deny decision.
 *
 * WHY OncePerRequestFilter:
 *   Spring's base class that guarantees this filter executes exactly once
 *   per HTTP request — even if the request is forwarded internally (e.g.
 *   error pages). Without it, the filter could run twice on a forwarded
 *   request and try to set the security context twice.
 *
 * WHAT THIS FILTER DOES:
 *   1. Read the "Authorization" header.
 *   2. If it starts with "Bearer ", extract the token string.
 *   3. Validate the token via JwtService.
 *   4. If valid, load the User entity by id and register it as the
 *      authenticated principal in the SecurityContextHolder.
 *   5. Pass the request to the next filter regardless of outcome.
 *
 * WHAT THIS FILTER DOES NOT DO:
 *   - It does NOT return a 401 response directly. If the token is missing
 *     or invalid, it simply does nothing and passes the request onward.
 *     Spring Security's own downstream filter detects that no authentication
 *     was set and returns the 401 automatically — keeping the filter simple.
 *   - It does NOT block public endpoints. SecurityConfig's permit rules
 *     allow those through even without an authenticated context.
 *
 * WHY WE INJECT UserRepository (not UserDetailsService):
 *   We look up the user by their numeric id (from the JWT's "sub" claim)
 *   using a direct primary-key lookup: O(1), always indexed. More
 *   importantly, it gives us the actual User entity as the principal.
 *   Future features (basket, orders) call:
 *       (User) SecurityContextHolder.getContext()
 *                .getAuthentication().getPrincipal()
 *   and cast directly to our User type. If we stored a Spring UserDetails
 *   object instead, callers would need to go back to the database to get
 *   the User — an extra round-trip on every authenticated request.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ── Step 1: read the Authorization header ─────────────────────
        String header = request.getHeader("Authorization");

        // If there's no header or it doesn't start with "Bearer ", skip.
        // This is normal for public endpoints (no token expected).
        // Pass the request through — SecurityConfig handles the response.
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: extract the token (strip "Bearer " prefix) ────────
        // "Bearer " is 7 characters. substring(7) gives us the raw JWT.
        String token = header.substring(7);

        // ── Step 3: validate the token ────────────────────────────────
        // isTokenValid() never throws — returns false for any problem.
        if (jwtService.isTokenValid(token)) {

            // ── Step 4: load the user and set the security context ─────
            Long userId = jwtService.extractUserId(token);

            // findById uses the primary key — always O(1), always indexed.
            // ifPresent: if the user was deleted after the token was issued,
            // we simply don't set auth — the request proceeds as anonymous.
            userRepository.findById(userId).ifPresent(user -> {

                // Build Spring Security's authentication token.
                // Parameters:
                //   principal   = the User entity (our own class — not UserDetails)
                //   credentials = null (we've already verified via JWT, no password needed)
                //   authorities = ["ROLE_USER"] (single role for now — no admin)
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                // Attach request metadata (IP address, session id) to the
                // auth token for Spring Security's audit/logging infrastructure.
                auth.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Register the authentication in the SecurityContextHolder.
                // This is what makes the rest of the request "see" the user
                // as authenticated. Any downstream code can now call:
                //   SecurityContextHolder.getContext().getAuthentication()
                // and get back this auth token.
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        // ── Step 5: always pass to the next filter ────────────────────
        // Whether we set auth or not, the request continues. SecurityConfig's
        // permit/deny rules decide what happens next.
        filterChain.doFilter(request, response);
    }
}
