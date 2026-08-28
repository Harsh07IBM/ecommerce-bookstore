package com.harsh.bookstore.exception;

/**
 * PaymentDeclinedException — thrown by OrderService when the supplied card number
 * is the simulated-decline sentinel "0000000000000000" (spec BR-10).
 *
 * Mapped to HTTP 402 Payment Required by GlobalExceptionHandler.
 *
 * No order is created and the basket is not cleared when this exception is thrown
 * (it propagates before any mutation occurs in OrderService.placeOrder).
 */
public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException() {
        super("Payment declined");
    }
}
