package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.OrderResponse;
import com.harsh.bookstore.dto.PaymentRequest;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


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
