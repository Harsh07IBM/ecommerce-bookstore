package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BasketItemDto;
import com.harsh.bookstore.dto.BasketResponse;
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
import com.harsh.bookstore.exception.GiftPointsExceedBasketTotalException;
import com.harsh.bookstore.exception.InsufficientGiftPointsException;
import com.harsh.bookstore.exception.InsufficientStockException;
import com.harsh.bookstore.exception.PaymentDeclinedException;
import com.harsh.bookstore.repository.BookRepository;
import com.harsh.bookstore.repository.DeliveryAddressRepository;
import com.harsh.bookstore.repository.OrderRepository;
import com.harsh.bookstore.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for OrderService.
 * No Spring context — service is instantiated directly with Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private BasketService basketService;
    @Mock private DeliveryAddressRepository addressRepository;
    @Mock private BookRepository bookRepository;
    @Mock private UserRepository userRepository;

    private OrderService orderService;

    private static final Long USER_ID    = 1L;
    private static final Long ADDRESS_ID = 10L;
    private static final Long BOOK_ID    = 100L;
    private static final int  FUTURE_YEAR = LocalDate.now().getYear() + 1;


    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository, basketService, addressRepository, bookRepository, userRepository);
    }


    // ==================================================================
    // HELPERS
    // ==================================================================

    private PaymentRequest validRequest() {
        PaymentRequest req = new PaymentRequest();
        req.setAddressId(ADDRESS_ID);
        req.setCardNumber("4111111111111111");
        req.setExpiryMonth(12);
        req.setExpiryYear(FUTURE_YEAR);
        req.setCvv("123");
        req.setCardholderName("Test User");
        req.setGiftPointsToRedeem(0);
        return req;
    }

    private BasketResponse basketWith(BigDecimal unitPrice, int qty) {
        BasketItemDto item = new BasketItemDto();
        item.setBookId(BOOK_ID);
        item.setTitle("Clean Code");
        item.setUnitPrice(unitPrice);
        item.setQuantity(qty);
        item.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));

        BasketResponse basket = new BasketResponse();
        basket.setItems(List.of(item));
        basket.setTotalItems(qty);
        basket.setBasketTotal(item.getLineTotal());
        return basket;
    }

    private DeliveryAddress address(Long userId) {
        DeliveryAddress a = new DeliveryAddress();
        a.setId(ADDRESS_ID);
        a.setUserId(userId);
        a.setRecipientName("Priya Sharma");
        a.setPhoneNumber("9876543210");
        a.setLine1("12 MG Road");
        a.setCity("Bengaluru");
        a.setState("Karnataka");
        a.setPincode("560001");
        return a;
    }

    private Book book(int stock) {
        Book b = new Book();
        b.setId(BOOK_ID);
        b.setTitle("Clean Code");
        b.setStockQuantity(stock);
        return b;
    }

    private User userWithPoints(int points) {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("test@example.com");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setPasswordHash("hash");
        u.setGiftPoints(points);
        return u;
    }

    /** Stub the happy path: basket + address + book + user + save. */
    private void stubHappyPath(BasketResponse basket) {
        stubHappyPath(basket, 0);
    }

    private void stubHappyPath(BasketResponse basket, int userPoints) {
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(USER_ID)));
        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book(10)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithPoints(userPoints)));

        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(42L);
            if (o.getOrderDate() == null) {
                o.setOrderDate(java.time.LocalDateTime.now());
            }
            return o;
        });
        lenient().when(basketService.clearBasket(USER_ID, null)).thenReturn(new BasketResponse());
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }


    // ==================================================================
    // SUCCESS TESTS
    // ==================================================================

    @Test
    void placeOrder_success_paidStatus() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getStatus()).isEqualTo("PAID");
        assertThat(response.getOrderId()).isEqualTo(42L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("Clean Code");
    }

    @Test
    void placeOrder_success_deliveryChargeFree() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);  // total >= 500
        stubHappyPath(basket);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getBasketTotal()).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    @Test
    void placeOrder_success_deliveryChargePaid() {
        BasketResponse basket = basketWith(new BigDecimal("299.00"), 1);  // total < 500
        stubHappyPath(basket);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void placeOrder_success_totalAmountCorrect() {
        BasketResponse basket = basketWith(new BigDecimal("299.00"), 1);  // total < 500 → charge = 50
        stubHappyPath(basket);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("349.00")); // 299 + 50
    }

    @Test
    void placeOrder_success_basketCleared() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket);

        orderService.placeOrder(USER_ID, validRequest());

        verify(basketService).clearBasket(USER_ID, null);
    }

    @Test
    void placeOrder_success_stockDecremented() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 2);
        stubHappyPath(basket);
        Book mockBook = book(10);
        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(mockBook));
        lenient().when(bookRepository.save(any(Book.class))).thenReturn(mockBook);

        orderService.placeOrder(USER_ID, validRequest());

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        assertThat(captor.getValue().getStockQuantity()).isEqualTo(8); // 10 - 2
    }

    @Test
    void placeOrder_success_addressSnapshot() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getDeliveryAddress().getRecipientName()).isEqualTo("Priya Sharma");
        assertThat(response.getDeliveryAddress().getCity()).isEqualTo("Bengaluru");
        assertThat(response.getDeliveryAddress().getPincode()).isEqualTo("560001");
    }

    @Test
    void placeOrder_success_estimatedDeliveryDate() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        String expected = LocalDate.now().plusDays(3).toString();
        assertThat(response.getEstimatedDeliveryDate()).isEqualTo(expected);
    }


    // ==================================================================
    // GIFT POINTS SUCCESS TESTS
    // ==================================================================

    @Test
    void placeOrder_zeroGiftPoints_awardsPointsOnly() {
        // basketTotal=600, delivery=0, redeem=0 → total=600, points=floor(600*0.05)=30
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket, 0);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getGiftPointsRedeemed()).isEqualTo(0);
        assertThat(response.getPointsAwarded()).isEqualTo(30);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("600.00"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGiftPoints()).isEqualTo(30); // 0 - 0 + 30
    }

    @Test
    void placeOrder_giftPoints_totalAmountReduced() {
        // basketTotal=600, delivery=0, redeem=50 → total=550
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket, 100);

        PaymentRequest req = validRequest();
        req.setGiftPointsToRedeem(50);

        OrderResponse response = orderService.placeOrder(USER_ID, req);

        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("550.00"));
    }

    @Test
    void placeOrder_giftPoints_pointsAwardedOnReducedTotal() {
        // basketTotal=199, delivery=50, redeem=0 → total=249, points=floor(249*0.05)=12
        BasketResponse basket = basketWith(new BigDecimal("199.00"), 1);
        stubHappyPath(basket, 0);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        assertThat(response.getPointsAwarded()).isEqualTo(12); // floor(249 * 0.05) = 12
    }

    @Test
    void placeOrder_giftPoints_pointsAwardedFloor() {
        // basketTotal=199, delivery=50, redeem=0 → total=249, points=12 (not 13)
        BasketResponse basket = basketWith(new BigDecimal("199.00"), 1);
        stubHappyPath(basket, 0);

        OrderResponse response = orderService.placeOrder(USER_ID, validRequest());

        // 249 * 0.05 = 12.45 → floor = 12
        assertThat(response.getPointsAwarded()).isEqualTo(12);
    }

    @Test
    void placeOrder_giftPoints_deductedAndAwarded() {
        // basketTotal=600, delivery=0, redeem=50 → total=550, points=floor(550*0.05)=27
        // user starts with 100 → ends with 100 - 50 + 27 = 77
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        stubHappyPath(basket, 100);

        PaymentRequest req = validRequest();
        req.setGiftPointsToRedeem(50);

        OrderResponse response = orderService.placeOrder(USER_ID, req);

        assertThat(response.getGiftPointsRedeemed()).isEqualTo(50);
        assertThat(response.getPointsAwarded()).isEqualTo(27); // floor(550 * 0.05)

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGiftPoints()).isEqualTo(77); // 100 - 50 + 27
    }


    // ==================================================================
    // GIFT POINTS FAILURE TESTS
    // ==================================================================

    @Test
    void placeOrder_insufficientGiftPoints_throws() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(USER_ID)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithPoints(20)));

        PaymentRequest req = validRequest();
        req.setGiftPointsToRedeem(50);  // user only has 20

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req))
                .isInstanceOf(InsufficientGiftPointsException.class)
                .hasMessage("Insufficient gift points");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_giftPointsExceedBasket_throws() {
        BasketResponse basket = basketWith(new BigDecimal("100.00"), 1);
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(USER_ID)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithPoints(500)));

        PaymentRequest req = validRequest();
        req.setGiftPointsToRedeem(200);  // basket total is only 100

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req))
                .isInstanceOf(GiftPointsExceedBasketTotalException.class)
                .hasMessage("Gift points exceed basket total");

        verify(orderRepository, never()).save(any());
    }


    // ==================================================================
    // EXISTING FAILURE TESTS
    // ==================================================================

    @Test
    void placeOrder_emptyBasket_throws() {
        BasketResponse emptyBasket = new BasketResponse();
        emptyBasket.setItems(List.of());
        emptyBasket.setBasketTotal(BigDecimal.ZERO);
        when(basketService.getBasket(USER_ID, null)).thenReturn(emptyBasket);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Basket is empty");
    }

    @Test
    void placeOrder_addressNotFound_throws() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, validRequest()))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void placeOrder_addressForbidden_throws() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(999L)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, validRequest()))
                .isInstanceOf(AddressAccessForbiddenException.class);
    }

    @Test
    void placeOrder_cardDeclined_throws() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 1);
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(USER_ID)));

        PaymentRequest req = validRequest();
        req.setCardNumber("0000000000000000");

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("Payment declined");

        verify(orderRepository, never()).save(any());
        verify(basketService, never()).clearBasket(any(), any());
    }

    @Test
    void placeOrder_insufficientStock_throws() {
        BasketResponse basket = basketWith(new BigDecimal("600.00"), 5);
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address(USER_ID)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithPoints(0)));
        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book(2)));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, validRequest()))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Clean Code");

        verify(orderRepository, never()).save(any());
        verify(basketService, never()).clearBasket(any(), any());
    }

    @Test
    void placeOrder_expiredYear_throws() {
        // expiryYear is validated first — before getBasket or addressRepository is called
        PaymentRequest req = validRequest();
        req.setExpiryYear(2000);

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiryYear");
    }
}
