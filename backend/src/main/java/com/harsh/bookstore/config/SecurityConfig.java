package com.harsh.bookstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


/**
 * SecurityConfig — declares every Spring Security rule for this application.
 *
 * WHAT @Configuration DOES:
 *   Marks this class as a source of @Bean definitions. Spring reads it at
 *   startup and registers each @Bean method's return value as a managed bean
 *   in the application context. Any other class can then inject those beans
 *   via constructor injection.
 *
 * WHAT @EnableWebSecurity DOES:
 *   Activates Spring Security's web security support. Without it, the
 *   SecurityFilterChain bean we declare below would be ignored.
 *
 * THE THREE BEANS DECLARED HERE:
 *
 *   1. SecurityFilterChain — the master set of rules: which endpoints are
 *      public, which require auth, how sessions work, where our JWT filter
 *      fits in. This is the heart of the security configuration.
 *
 *   2. BCryptPasswordEncoder — declared here (not in UserService) so it can
 *      be injected anywhere without a circular dependency. UserService needs
 *      it to hash passwords; Spring Security needs it to verify passwords
 *      during its own auth flow. One shared bean satisfies both.
 *
 *   3. AuthenticationManager — Spring Security's central "can this user log
 *      in?" coordinator. We expose it as a bean so UserService or future
 *      code can inject it if needed. Obtained from AuthenticationConfiguration
 *      which Spring wires automatically.
 *
 * WHY CSRF IS DISABLED:
 *   CSRF (Cross-Site Request Forgery) attacks work by tricking a browser into
 *   sending a request using cookies the user already has. Our API uses JWT
 *   tokens in the Authorization header — there are no cookies, so there is
 *   nothing for a CSRF attack to exploit. Disabling CSRF is the correct and
 *   standard choice for stateless REST APIs.
 *
 * WHY SESSION IS IF_REQUIRED (changed from STATELESS in FEAT-06):
 *   FEAT-06 (Shopping Basket) requires a session cookie to identify guest
 *   visitors. IF_REQUIRED tells Spring to create an HttpSession only when
 *   one is actually needed — i.e. the first time a guest calls a basket
 *   endpoint. Authenticated requests still carry a JWT and will not create
 *   a session. This is the minimal change that supports guest baskets while
 *   keeping JWT-only endpoints effectively stateless.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }


    /**
     * The main security rule set.
     *
     * READING THE PERMIT RULES IN ORDER (most specific first):
     *
     *   GET  /api/books/**    — all catalogue read endpoints (FEAT-01/02/03)
     *   GET  /api/categories  — category list (FEAT-02)
     *   POST /api/auth/**     — register + login (this feature)
     *   /h2-console/**        — H2 browser console (dev only)
     *   anyRequest()          — everything else requires authentication
     *
     * IMPORTANT: rules are evaluated IN ORDER and the first match wins.
     * The catch-all .anyRequest().authenticated() at the end protects
     * every endpoint we add in future features (basket, orders, etc.)
     * without needing to add individual rules for each one.
     *
     * addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class):
     *   Inserts our JWT filter INTO Spring Security's filter chain, just
     *   before Spring's own username/password filter. This means: on every
     *   request, our filter runs first, sets the authentication context if
     *   a valid JWT is present, and then Spring Security's own filters see
     *   an already-authenticated request and allow it through.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — JWT in Authorization header, no cookies (see class Javadoc)
            .csrf(csrf -> csrf.disable())

            // Create sessions only when needed (guest basket support — FEAT-06).
            // See class Javadoc for the rationale.
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // Permit/deny rules
            .authorizeHttpRequests(auth -> auth
                    // All catalogue read endpoints stay public (FEAT-01, 02, 03)
                    .requestMatchers(HttpMethod.GET, "/api/books/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                    // Auth endpoints must be public — you can't require a token to log in
                    .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                    // H2 console — dev only, allow all methods on this path
                    .requestMatchers("/h2-console/**").permitAll()
                    // Basket endpoints are open to guests and authenticated users (FEAT-06)
                    .requestMatchers("/api/basket/**").permitAll()
                    // Every other endpoint (orders, etc.) requires a valid JWT
                    .anyRequest().authenticated()
            )

            // H2 console uses <iframe> tags. Spring Security's default
            // X-Frame-Options: DENY header blocks iframes. Disable that
            // header so the H2 console renders correctly in the browser.
            // Safe because the console is only accessible on localhost.
            .headers(headers ->
                    headers.frameOptions(fo -> fo.disable()))

            // Insert our JWT filter before Spring's own credential filter.
            // Spring Security auto-discovers UserService as the UserDetailsService
            // bean (it implements UserDetailsService) without explicit wiring here.
            // Order matters: JwtAuthFilter sets the auth context first, so
            // Spring Security's downstream filters see an authenticated request.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    /**
     * BCrypt password encoder bean.
     *
     * WHY DECLARED HERE (not in UserService):
     *   UserService needs BCryptPasswordEncoder to hash and verify passwords.
     *   Spring Security's AuthenticationManager also needs a PasswordEncoder
     *   to verify passwords during its own auth flow. Declaring it here as a
     *   shared @Bean avoids creating two separate instances and prevents the
     *   circular dependency that would occur if UserService declared it
     *   (UserService ← SecurityConfig ← UserService = circular).
     *
     * WORK FACTOR:
     *   BCryptPasswordEncoder() with no arguments uses the default work factor
     *   of 10 (2^10 = 1024 iterations). This is the recommended default —
     *   slow enough to make brute-force attacks impractical, fast enough that
     *   a legitimate login takes ~100ms (imperceptible to users).
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    /**
     * AuthenticationManager bean.
     *
     * Spring Security's central coordinator for "can this user authenticate?"
     * questions. We expose it as a @Bean so that code needing to programmatically
     * trigger authentication (e.g. future admin features) can inject it.
     *
     * AuthenticationConfiguration is auto-provided by Spring Security's
     * auto-configuration — we just ask it for the manager it built.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
