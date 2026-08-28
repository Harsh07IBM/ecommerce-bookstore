package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.AddItemRequest;
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
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.GiftPointsExceedBasketTotalException;
import com.harsh.bookstore.exception.InsufficientGiftPointsException;
import com.harsh.bookstore.exception.InsufficientStockException;
import com.harsh.bookstore.exception.MaxQuantityExceededException;
import com.harsh.bookstore.exception.OrderAccessForbiddenException;
import com.harsh.bookstore.exception.OrderNotFoundException;
import com.harsh.bookstore.exception.OutOfStockException;
import com.harsh.bookstore.exception.PaymentDeclinedException;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.DeliveryAddressRepository;
import com.harsh.bookstore.repository.OrderRepository;
import com.harsh.bookstore.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * OrderService — orchestrates the full payment and order creation flow (FEAT-08/09).
 *
 * TRANSACTION:
 *   placeOrder() is @Transactional — all mutations (stock decrement, order save,
 *   basket clear, user balance update) run in one transaction. An exception at
 *   any step rolls back all preceding mutations.
 *
 * OPERATION ORDER (design D-07):
 *   1.  expiryYear validation
 *   2.  empty basket check
 *   3.  address ownership check
 *   4.  card decline check
 *   5a. load User
 *   5b. gift point balance validation
 *   5c. gift points vs basket total validation
 *   6.  compute charges (delivery charge, gift discount, total, points awarded)
 *   7.  stock validation pass (no mutations)
 *   8.  stock decrement pass
 *   9.  build + save Order
 *   10. clear basket
 *   11. mutate + save User balance
 *   12. return response
 *
 * CARD DETAILS (spec BR-17):
 *   Card fields are used only in-memory for format validation and the decline
 *   check. They are never stored anywhere.
 */
@Service
public class OrderService {

    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal DELIVERY_CHARGE_AMOUNT  = new BigDecimal("50.00");
    private static final String     DECLINE_CARD_NUMBER     = "0000000000000000";
    private static final BigDecimal POINTS_RATE             = new BigDecimal("0.05");

    private final OrderRepository orderRepository;
    private final BasketService basketService;
    private final DeliveryAddressRepository addressRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        BasketService basketService,
                        DeliveryAddressRepository addressRepository,
                        BookRepository bookRepository,
                        UserRepository userRepository) {
        this.orderRepository   = orderRepository;
        this.basketService     = basketService;
        this.addressRepository = addressRepository;
        this.bookRepository    = bookRepository;
        this.userRepository    = userRepository;
    }


    // ==================================================================
    // PUBLIC API
    // ==================================================================

    /**
     * Return all orders for the authenticated user, sorted newest first (FEAT-10).
     *
     * @param userId the authenticated user's ID
     * @return list of OrderResponse sorted by orderDate descending; empty list if none
     */
    public List<OrderResponse> getOrders(Long userId) {
        return orderRepository.findAllByUserId(userId)
                .stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .map(this::toResponse)
                .toList();
    }


    /**
     * Return a single order by ID with ownership check (FEAT-10).
     *
     * @param userId  the authenticated user's ID
     * @param orderId the order to retrieve
     * @return OrderResponse if the order belongs to the user
     * @throws OrderNotFoundException          if no order with orderId exists
     * @throws OrderAccessForbiddenException   if the order belongs to another user
     */
    public OrderResponse getOrderById(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (!order.getUserId().equals(userId)) {
            throw new OrderAccessForbiddenException();
        }
        return toResponse(order);
    }


    /**
     * Re-add all items from a previous order to the current basket (FEAT-11).
     * Items that are out of stock, exceed max quantity, or no longer exist are silently skipped.
     *
     * @param userId  the authenticated user's ID
     * @param orderId the order to re-purchase
     * @return the updated basket after adding available items
     * @throws OrderNotFoundException        if the order does not exist
     * @throws OrderAccessForbiddenException if the order belongs to another user
     */
    public BasketResponse buyAgain(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (!order.getUserId().equals(userId)) {
            throw new OrderAccessForbiddenException();
        }
        for (OrderItem item : order.getItems()) {
            try {
                AddItemRequest req = new AddItemRequest();
                req.setBookId(item.getBookId());
                req.setQuantity(1);
                basketService.addItem(userId, null, req);
            } catch (OutOfStockException | MaxQuantityExceededException | BookNotFoundException e) {
                // skip silently — spec BR-05, BR-06, BR-07
            }
        }
        return basketService.getBasket(userId, null);
    }


    /**
     * Validate, charge, and create an order for the authenticated user.
     *
     * @param userId the authenticated user's ID (from JWT principal)
     * @param req    the validated payment request body
     * @return the saved OrderResponse with status PAID
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, PaymentRequest req) {

        // Step 1 — expiryYear runtime validation
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

        // Step 5a — load user (needed for gift point checks)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        // Step 5b — gift point balance check (spec BR-05)
        if (req.getGiftPointsToRedeem() > user.getGiftPoints()) {
            throw new InsufficientGiftPointsException();
        }

        // Step 5c — gift points must not exceed basket total (spec BR-06)
        if (new BigDecimal(req.getGiftPointsToRedeem()).compareTo(basket.getBasketTotal()) > 0) {
            throw new GiftPointsExceedBasketTotalException();
        }

        // Step 6 — compute charges
        BigDecimal deliveryCharge =
                basket.getBasketTotal().compareTo(FREE_DELIVERY_THRESHOLD) >= 0
                        ? BigDecimal.ZERO
                        : DELIVERY_CHARGE_AMOUNT;
        BigDecimal giftDiscount  = new BigDecimal(req.getGiftPointsToRedeem());
        BigDecimal totalAmount   = basket.getBasketTotal().add(deliveryCharge).subtract(giftDiscount);
        int pointsAwarded        = totalAmount
                                        .multiply(POINTS_RATE)
                                        .setScale(0, RoundingMode.FLOOR)
                                        .intValue();
        String estimatedDeliveryDate = LocalDate.now().plusDays(3).toString();

        // Step 7 — stock validation pass (first pass — no mutations yet)
        for (BasketItemDto item : basket.getItems()) {
            Book book = bookRepository.findById(item.getBookId())
                    .orElseThrow(() -> new BookNotFoundException(item.getBookId()));
            if (book.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(item.getTitle());
            }
        }

        // Step 8 — stock decrement pass (second pass — all validations passed)
        for (BasketItemDto item : basket.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).get();
            book.setStockQuantity(book.getStockQuantity() - item.getQuantity());
            bookRepository.save(book);
        }

        // Step 9 — build and save Order
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PAID);
        order.setBasketTotal(basket.getBasketTotal());
        order.setDeliveryCharge(deliveryCharge);
        order.setGiftPointsRedeemed(req.getGiftPointsToRedeem());
        order.setTotalAmount(totalAmount);
        order.setPointsAwarded(pointsAwarded);
        order.setEstimatedDeliveryDate(estimatedDeliveryDate);
        // address snapshot (spec BR-16)
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

        // Step 10 — clear basket (spec BR-13)
        basketService.clearBasket(userId, null);

        // Step 11 — mutate and save user balance (spec BR-08 / BR-09)
        user.setGiftPoints(user.getGiftPoints() - req.getGiftPointsToRedeem() + pointsAwarded);
        userRepository.save(user);

        // Step 12 — build and return response
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
        response.setGiftPointsRedeemed(order.getGiftPointsRedeemed());
        response.setTotalAmount(order.getTotalAmount());
        response.setPointsAwarded(order.getPointsAwarded());
        response.setEstimatedDeliveryDate(order.getEstimatedDeliveryDate());
        response.setDeliveryAddress(addr);
        return response;
    }
}
