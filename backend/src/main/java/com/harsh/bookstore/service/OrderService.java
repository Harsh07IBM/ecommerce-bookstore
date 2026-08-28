package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BasketItemDto;
import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.dto.OrderAddressSnapshot;
import com.harsh.bookstore.dto.OrderItemResponse;
import com.harsh.bookstore.dto.OrderResponse;
import com.harsh.bookstore.dto.PaymentRequest;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.DeliveryAddress;
import com.harsh.bookstore.entity.Order;
import com.harsh.bookstore.entity.OrderItem;
import com.harsh.bookstore.entity.OrderStatus;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.InsufficientStockException;
import com.harsh.bookstore.exception.PaymentDeclinedException;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.DeliveryAddressRepository;
import com.harsh.bookstore.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


/**
 * OrderService — orchestrates the full payment and order creation flow (FEAT-08).
 *
 * TRANSACTION:
 *   placeOrder() is @Transactional — all mutations (stock decrement, order save,
 *   basket clear) run in one transaction. An exception thrown at any step rolls
 *   back all preceding mutations.
 *
 * OPERATION ORDER (design D-07):
 *   1. Validate all inputs (year, empty basket, address, card decline)
 *   2. Check all stock levels (first pass — no mutations)
 *   3. Decrement all stock (second pass)
 *   4. Save Order (cascade saves OrderItems)
 *   5. Clear basket
 *
 * CARD DETAILS (spec BR-17 / design D-09):
 *   Card fields from PaymentRequest are used only in-memory for format validation
 *   and the decline check. They are never stored anywhere.
 */
@Service
public class OrderService {

    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal DELIVERY_CHARGE_AMOUNT  = new BigDecimal("50.00");
    private static final String     DECLINE_CARD_NUMBER     = "0000000000000000";

    private final OrderRepository orderRepository;
    private final BasketService basketService;
    private final DeliveryAddressRepository addressRepository;
    private final BookRepository bookRepository;

    public OrderService(OrderRepository orderRepository,
                        BasketService basketService,
                        DeliveryAddressRepository addressRepository,
                        BookRepository bookRepository) {
        this.orderRepository   = orderRepository;
        this.basketService     = basketService;
        this.addressRepository = addressRepository;
        this.bookRepository    = bookRepository;
    }


    // ==================================================================
    // PUBLIC API
    // ==================================================================

    /**
     * Validate, charge, and create an order for the authenticated user.
     *
     * @param userId the authenticated user's ID (from JWT principal)
     * @param req    the validated payment request body
     * @return the saved OrderResponse with status PAID
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, PaymentRequest req) {

        // Step 1 — expiryYear runtime validation (design D-06)
        if (req.getExpiryYear() < LocalDate.now().getYear()) {
            throw new IllegalArgumentException("expiryYear must be the current year or later");
        }

        // Step 2 — empty basket guard (spec BR-02)
        BasketResponse basket = basketService.getBasket(userId, null);
        if (basket.getItems().isEmpty()) {
            throw new IllegalArgumentException("Basket is empty");
        }

        // Step 3 — address ownership check (spec BR-03)
        DeliveryAddress address = addressRepository.findById(req.getAddressId())
                .orElseThrow(() -> new AddressNotFoundException(req.getAddressId()));
        if (!address.getUserId().equals(userId)) {
            throw new AddressAccessForbiddenException();
        }

        // Step 4 — simulated card decline (spec BR-10)
        if (DECLINE_CARD_NUMBER.equals(req.getCardNumber())) {
            throw new PaymentDeclinedException();
        }

        // Step 5 — compute charges (design D-05)
        BigDecimal deliveryCharge =
                basket.getBasketTotal().compareTo(FREE_DELIVERY_THRESHOLD) >= 0
                        ? BigDecimal.ZERO
                        : DELIVERY_CHARGE_AMOUNT;
        BigDecimal totalAmount = basket.getBasketTotal().add(deliveryCharge);
        String estimatedDeliveryDate = LocalDate.now().plusDays(3).toString();

        // Step 6 — stock validation pass (design D-07, first pass — no mutations yet)
        for (BasketItemDto item : basket.getItems()) {
            Book book = bookRepository.findById(item.getBookId())
                    .orElseThrow(() -> new BookNotFoundException(item.getBookId()));
            if (book.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(item.getTitle());
            }
        }

        // Step 7 — stock decrement pass (design D-07, second pass — all validations passed)
        for (BasketItemDto item : basket.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).get();
            book.setStockQuantity(book.getStockQuantity() - item.getQuantity());
            bookRepository.save(book);
        }

        // Step 8 — build and save Order
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PAID);
        order.setBasketTotal(basket.getBasketTotal());
        order.setDeliveryCharge(deliveryCharge);
        order.setTotalAmount(totalAmount);
        order.setEstimatedDeliveryDate(estimatedDeliveryDate);
        // address snapshot (spec BR-16 / design D-02)
        order.setRecipientName(address.getRecipientName());
        order.setPhoneNumber(address.getPhoneNumber());
        order.setLine1(address.getLine1());
        order.setLine2(address.getLine2());
        order.setCity(address.getCity());
        order.setState(address.getState());
        order.setPincode(address.getPincode());
        // order items
        List<OrderItem> orderItems = new ArrayList<>();
        for (BasketItemDto item : basket.getItems()) {
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setBookId(item.getBookId());
            oi.setTitle(item.getTitle());
            oi.setQuantity(item.getQuantity());
            oi.setUnitPrice(item.getUnitPrice());
            oi.setLineTotal(item.getLineTotal());
            orderItems.add(oi);
        }
        order.setItems(orderItems);

        Order saved = orderRepository.save(order); // CascadeType.ALL saves OrderItems

        // Step 9 — clear basket (spec BR-13 / design D-08)
        basketService.clearBasket(userId, null);

        // Step 10 — build and return response
        return toResponse(saved);
    }


    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    private OrderResponse toResponse(Order order) {
        // map items
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem oi : order.getItems()) {
            OrderItemResponse ir = new OrderItemResponse();
            ir.setBookId(oi.getBookId());
            ir.setTitle(oi.getTitle());
            ir.setQuantity(oi.getQuantity());
            ir.setUnitPrice(oi.getUnitPrice());
            ir.setLineTotal(oi.getLineTotal());
            itemResponses.add(ir);
        }

        // map address snapshot
        OrderAddressSnapshot addr = new OrderAddressSnapshot();
        addr.setRecipientName(order.getRecipientName());
        addr.setPhoneNumber(order.getPhoneNumber());
        addr.setLine1(order.getLine1());
        addr.setLine2(order.getLine2());
        addr.setCity(order.getCity());
        addr.setState(order.getState());
        addr.setPincode(order.getPincode());

        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setStatus(order.getStatus().name());
        response.setOrderDate(order.getOrderDate().toString());
        response.setItems(itemResponses);
        response.setBasketTotal(order.getBasketTotal());
        response.setDeliveryCharge(order.getDeliveryCharge());
        response.setTotalAmount(order.getTotalAmount());
        response.setEstimatedDeliveryDate(order.getEstimatedDeliveryDate());
        response.setDeliveryAddress(addr);
        return response;
    }
}
