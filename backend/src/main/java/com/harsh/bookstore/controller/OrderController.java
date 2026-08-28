package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.dto.OrderConfirmationResponse;
import com.harsh.bookstore.dto.OrderResponse;
import com.harsh.bookstore.dto.PaymentRequest;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * OrderController — handles POST /api/orders (FEAT-08 payment).
 *
 * AUTH:
 *   All endpoints require a valid JWT. Spring Security rejects unauthenticated
 *   requests before the method is reached (401 via HttpStatusEntryPoint).
 *   The Authentication parameter is therefore guaranteed non-null.
 *
 * userId EXTRACTION:
 *   Consistent with AddressController and CheckoutController:
 *   ((User) authentication.getPrincipal()).getId()
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }


    /**
     * List all orders for the authenticated user, newest first.
     *
     * @param authentication Spring Security principal — always non-null (JWT required)
     * @return 200 with list of OrderResponse (empty array if none)
     */
    @GetMapping
    public List<OrderResponse> listOrders(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return orderService.getOrders(user.getId());
    }


    /**
     * Get a single order by ID.
     *
     * @param id             the order ID from the path
     * @param authentication Spring Security principal — always non-null (JWT required)
     * @return 200 with OrderResponse; 403 if wrong owner; 404 if not found
     */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id,
                                  Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return orderService.getOrderById(user.getId(), id);
    }


    /**
     * Cancel a PAID order within 48 hours of placement.
     *
     * @param id             the order ID from the path
     * @param authentication Spring Security principal — always non-null (JWT required)
     * @return 200 with updated OrderResponse (status = CANCELLED)
     */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long id,
                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return orderService.cancelOrder(user.getId(), id);
    }


    /**
     * Re-add all items from a previous order to the current basket.
     *
     * @param id             the order ID from the path
     * @param authentication Spring Security principal — always non-null (JWT required)
     * @return 200 with updated BasketResponse
     */
    @PostMapping("/{id}/buy-again")
    public BasketResponse buyAgain(@PathVariable Long id,
                                   Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return orderService.buyAgain(user.getId(), id);
    }


    /**
     * Get the purchase confirmation for a placed order (FEAT-13).
     *
     * @param id             the order ID from the path
     * @param authentication Spring Security principal — always non-null (JWT required)
     * @return 200 with OrderConfirmationResponse; 403 if wrong owner; 404 if not found
     */
    @GetMapping("/{id}/confirmation")
    public OrderConfirmationResponse getConfirmation(@PathVariable Long id,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return orderService.getConfirmation(user.getId(), id);
    }


    /**
     * Place an order — validate payment, decrement stock, create order, clear basket.
     *
     * @param req            the validated payment request body
     * @param authentication Spring Security principal — always non-null here (JWT required)
     * @return 201 Created with the full OrderResponse
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody PaymentRequest req,
                                    Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return orderService.placeOrder(user.getId(), req);
    }
}
