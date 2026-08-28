package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.CheckoutSummaryResponse;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.service.CheckoutService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * CheckoutController — HTTP entry point for the checkout summary endpoint (FEAT-07).
 *
 * AUTHENTICATION:
 *   Requires a valid JWT (same as AddressController). anyRequest().authenticated()
 *   in SecurityConfig returns 401 before this controller is reached when no JWT
 *   is present.
 *
 * MISSING addressId PARAMETER:
 *   @RequestParam without required=false defaults to required=true. When
 *   addressId is absent from the query string, Spring MVC throws
 *   MissingServletRequestParameterException → 400 Bad Request automatically,
 *   before the handler method is invoked. No service call or extra handler needed.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }


    /**
     * GET /api/checkout/summary?addressId={id}
     * Returns basket items, delivery charge, estimated delivery date, and chosen address.
     * addressId is required — omitting it returns 400 automatically (see class Javadoc).
     */
    @GetMapping("/summary")
    public CheckoutSummaryResponse getCheckoutSummary(
            @RequestParam Long addressId,
            Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return checkoutService.getCheckoutSummary(userId, addressId);
    }
}
