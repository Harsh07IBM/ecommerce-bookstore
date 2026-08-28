package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.dto.CheckoutSummaryResponse;
import com.harsh.bookstore.dto.DeliveryAddressDto;
import com.harsh.bookstore.entity.DeliveryAddress;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.repository.DeliveryAddressRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * CheckoutService — builds the read-only checkout summary (FEAT-07).
 *
 * BASKET DELEGATION (design D-05):
 *   Basket data is read via BasketService.getBasket(userId, null). Passing null
 *   as sessionId is the established pattern for authenticated users (same call
 *   BasketController uses). This avoids duplicating basket-loading logic.
 *
 * DELIVERY CHARGE (BR-10):
 *   basketTotal >= 500 → ₹0 (free delivery)
 *   basketTotal <  500 → ₹50
 *   BigDecimal.compareTo is used (not equals) because equals is scale-sensitive:
 *   new BigDecimal("500").equals(new BigDecimal("500.00")) is false.
 *
 * DELIVERY DATE (BR-11):
 *   LocalDate.now().plusDays(3).toString() → "YYYY-MM-DD".
 *   Returned as String to avoid Jackson date configuration (design D-08).
 *
 * EMPTY BASKET (BR-13 / AC-18):
 *   Throws IllegalArgumentException("Basket is empty"), caught by the existing
 *   GlobalExceptionHandler → 400. No new exception class needed (design D-06).
 *
 * READ-ONLY (BR-14):
 *   This method never calls save, delete, or any mutating repository method.
 */
@Service
public class CheckoutService {

    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal DELIVERY_CHARGE = new BigDecimal("50.00");

    private final BasketService basketService;
    private final DeliveryAddressRepository addressRepository;

    public CheckoutService(BasketService basketService,
                           DeliveryAddressRepository addressRepository) {
        this.basketService = basketService;
        this.addressRepository = addressRepository;
    }


    /**
     * Build a pre-payment checkout summary for the authenticated user.
     *
     * @param userId    id of the authenticated user (from JWT principal)
     * @param addressId id of the chosen delivery address (must belong to userId)
     * @return CheckoutSummaryResponse with basket items, totals, delivery charge,
     *         estimated delivery date, and the chosen address
     * @throws IllegalArgumentException          if the basket is empty (→ 400)
     * @throws AddressNotFoundException          if addressId does not exist (→ 404)
     * @throws AddressAccessForbiddenException   if address belongs to another user (→ 403)
     */
    public CheckoutSummaryResponse getCheckoutSummary(Long userId, Long addressId) {

        // Step 1 — load basket via BasketService (authenticated path: sessionId = null)
        BasketResponse basket = basketService.getBasket(userId, null);

        // Step 2 — empty basket guard (BR-13 / AC-18)
        if (basket.getItems().isEmpty()) {
            throw new IllegalArgumentException("Basket is empty");
        }

        // Step 3 — load and validate the delivery address
        DeliveryAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));

        if (!address.getUserId().equals(userId)) {
            throw new AddressAccessForbiddenException();
        }

        // Step 4 — calculate delivery charge (BR-10)
        BigDecimal deliveryCharge =
                basket.getBasketTotal().compareTo(FREE_DELIVERY_THRESHOLD) >= 0
                        ? BigDecimal.ZERO
                        : DELIVERY_CHARGE;

        // Step 5 — estimated delivery date: today + 3 calendar days (BR-11)
        String estimatedDeliveryDate = LocalDate.now().plusDays(3).toString();

        // Step 6 — map address to the lean checkout DTO (no userId / isDefault)
        DeliveryAddressDto addressDto = new DeliveryAddressDto();
        addressDto.setId(address.getId());
        addressDto.setRecipientName(address.getRecipientName());
        addressDto.setPhoneNumber(address.getPhoneNumber());
        addressDto.setLine1(address.getLine1());
        addressDto.setLine2(address.getLine2());
        addressDto.setCity(address.getCity());
        addressDto.setState(address.getState());
        addressDto.setPincode(address.getPincode());

        // Step 7 — assemble and return the response
        CheckoutSummaryResponse response = new CheckoutSummaryResponse();
        response.setItems(basket.getItems());
        response.setBasketTotal(basket.getBasketTotal());
        response.setDeliveryCharge(deliveryCharge);
        response.setEstimatedDeliveryDate(estimatedDeliveryDate);
        response.setDeliveryAddress(addressDto);
        return response;
    }
}
