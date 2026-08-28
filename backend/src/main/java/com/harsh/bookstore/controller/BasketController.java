package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.AddItemRequest;
import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.dto.UpdateItemRequest;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.service.BasketService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * BasketController — HTTP entry point for the 5 basket endpoints (FEAT-06).
 *
 * IDENTITY RESOLUTION:
 *   Every handler calls resolveIdentity(auth, session) which returns a
 *   two-element array [userId, sessionId] where exactly one is non-null:
 *
 *     - Authenticated request (JWT present): JwtAuthFilter has already placed
 *       the User entity as the Authentication principal. We cast and extract
 *       the userId. sessionId is ignored.
 *
 *     - Guest request (no JWT): auth is null. We use HttpSession.getId()
 *       as the stable session key. With SessionCreationPolicy.IF_REQUIRED,
 *       Spring creates a session the first time a guest touches a basket
 *       endpoint; subsequent requests reuse the same session cookie.
 *
 * ALL ENDPOINTS RETURN BasketResponse:
 *   Every mutation returns the current basket state after the operation,
 *   so clients never need a separate GET after a write.
 *
 * VALIDATION:
 *   @Valid on @RequestBody parameters fires Bean Validation before the
 *   service is called. Failures are caught by GlobalExceptionHandler → 400.
 */
@RestController
@RequestMapping("/api/basket")
public class BasketController {

    private final BasketService basketService;

    public BasketController(BasketService basketService) {
        this.basketService = basketService;
    }


    /**
     * GET /api/basket
     * Returns the caller's current basket (empty if first visit).
     */
    @GetMapping
    public BasketResponse getBasket(Authentication authentication,
                                    HttpSession session) {
        Object[] id = resolveIdentity(authentication, session);
        return basketService.getBasket((Long) id[0], (String) id[1]);
    }


    /**
     * POST /api/basket/items
     * Add a book to the basket, or increment its quantity if already present.
     */
    @PostMapping("/items")
    public BasketResponse addItem(@Valid @RequestBody AddItemRequest req,
                                  Authentication authentication,
                                  HttpSession session) {
        Object[] id = resolveIdentity(authentication, session);
        return basketService.addItem((Long) id[0], (String) id[1], req);
    }


    /**
     * PUT /api/basket/items/{bookId}
     * Set the quantity for a specific book. quantity=0 removes the item.
     */
    @PutMapping("/items/{bookId}")
    public BasketResponse updateItem(@PathVariable Long bookId,
                                     @Valid @RequestBody UpdateItemRequest req,
                                     Authentication authentication,
                                     HttpSession session) {
        Object[] id = resolveIdentity(authentication, session);
        return basketService.updateItem((Long) id[0], (String) id[1],
                bookId, req.getQuantity());
    }


    /**
     * DELETE /api/basket/items/{bookId}
     * Remove a specific book from the basket entirely.
     */
    @DeleteMapping("/items/{bookId}")
    public BasketResponse removeItem(@PathVariable Long bookId,
                                     Authentication authentication,
                                     HttpSession session) {
        Object[] id = resolveIdentity(authentication, session);
        return basketService.removeItem((Long) id[0], (String) id[1], bookId);
    }


    /**
     * DELETE /api/basket
     * Remove all items from the basket.
     */
    @DeleteMapping
    public BasketResponse clearBasket(Authentication authentication,
                                      HttpSession session) {
        Object[] id = resolveIdentity(authentication, session);
        return basketService.clearBasket((Long) id[0], (String) id[1]);
    }


    // ==================================================================
    // PRIVATE HELPER
    // ==================================================================

    /**
     * Extract the identity key for basket resolution.
     *
     * Returns a two-element array where exactly one element is non-null:
     *   [0] = userId  (Long)  — set when a valid JWT principal is present
     *   [1] = sessionId (String) — set when the caller is a guest
     *
     * The instanceof pattern-match handles the null-auth guest path safely.
     */
    private Object[] resolveIdentity(Authentication auth, HttpSession session) {
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return new Object[]{ user.getId(), null };
        }
        return new Object[]{ null, session.getId() };
    }
}
