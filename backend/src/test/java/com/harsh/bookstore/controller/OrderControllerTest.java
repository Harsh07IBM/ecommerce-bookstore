package com.harsh.bookstore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.OrderAddressSnapshot;
import com.harsh.bookstore.dto.OrderItemResponse;
import com.harsh.bookstore.dto.OrderResponse;
import com.harsh.bookstore.dto.PaymentRequest;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.exception.GiftPointsExceedBasketTotalException;
import com.harsh.bookstore.exception.InsufficientGiftPointsException;
import com.harsh.bookstore.exception.InsufficientStockException;
import com.harsh.bookstore.exception.OrderAccessForbiddenException;
import com.harsh.bookstore.exception.OrderNotFoundException;
import com.harsh.bookstore.exception.PaymentDeclinedException;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.JwtService;
import com.harsh.bookstore.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for OrderController.
 *
 * AUTHENTICATION IN @WebMvcTest:
 *   Uses SecurityMockMvcRequestPostProcessors.authentication() to inject a
 *   pre-built Authentication with our User principal — no real JWT needed.
 *
 * 401 TEST:
 *   Performed with no authentication() post-processor. anyRequest().authenticated()
 *   causes Spring Security to reject the request before the controller is reached.
 */
@WebMvcTest(value = OrderController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;


    // ==================================================================
    // HELPERS
    // ==================================================================

    private UsernamePasswordAuthenticationToken userAuth() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hash");
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private PaymentRequest validRequest() {
        PaymentRequest req = new PaymentRequest();
        req.setAddressId(10L);
        req.setCardNumber("4111111111111111");
        req.setExpiryMonth(12);
        req.setExpiryYear(2099);
        req.setCvv("123");
        req.setCardholderName("Test User");
        req.setGiftPointsToRedeem(0);
        return req;
    }

    private OrderResponse paidResponse() {
        OrderItemResponse item = new OrderItemResponse();
        item.setBookId(100L);
        item.setTitle("Clean Code");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("599.00"));
        item.setLineTotal(new BigDecimal("599.00"));

        OrderAddressSnapshot addr = new OrderAddressSnapshot();
        addr.setRecipientName("Test User");
        addr.setPhoneNumber("9876543210");
        addr.setLine1("1 Main St");
        addr.setCity("Mumbai");
        addr.setState("Maharashtra");
        addr.setPincode("400001");

        OrderResponse resp = new OrderResponse();
        resp.setOrderId(42L);
        resp.setStatus("PAID");
        resp.setOrderDate("2025-09-01T10:00:00");
        resp.setItems(List.of(item));
        resp.setBasketTotal(new BigDecimal("599.00"));
        resp.setDeliveryCharge(BigDecimal.ZERO);
        resp.setTotalAmount(new BigDecimal("599.00"));
        resp.setEstimatedDeliveryDate("2025-09-04");
        resp.setDeliveryAddress(addr);
        return resp;
    }


    // ==================================================================
    // SUCCESS
    // ==================================================================

    @Test
    void placeOrder_returns201() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenReturn(paidResponse());

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.orderId").value(42));
    }


    // ==================================================================
    // 400 — BEAN VALIDATION FAILURES
    // ==================================================================

    @Test
    void placeOrder_returns400_invalidCardNumber() throws Exception {
        PaymentRequest req = validRequest();
        req.setCardNumber("123");   // not 16 digits

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_invalidExpiryMonth() throws Exception {
        PaymentRequest req = validRequest();
        req.setExpiryMonth(13);

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_invalidCvv() throws Exception {
        PaymentRequest req = validRequest();
        req.setCvv("12AB");   // not 3 numeric digits

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_blankCardholderName() throws Exception {
        PaymentRequest req = validRequest();
        req.setCardholderName("");

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_negativeGiftPoints() throws Exception {
        PaymentRequest req = validRequest();
        req.setGiftPointsToRedeem(-1);

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_returns400_emptyBasket() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new IllegalArgumentException("Basket is empty"));

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Basket is empty"));
    }

    @Test
    void placeOrder_returns400_insufficientStock() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new InsufficientStockException("Clean Code"));

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient stock for: Clean Code"));
    }

    @Test
    void placeOrder_returns400_insufficientGiftPoints() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new InsufficientGiftPointsException());

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient gift points"));
    }

    @Test
    void placeOrder_returns400_giftPointsExceedBasket() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new GiftPointsExceedBasketTotalException());

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Gift points exceed basket total"));
    }


    // ==================================================================
    // 401 — NO JWT
    // ==================================================================

    @Test
    void placeOrder_returns401_noJwt() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }


    // ==================================================================
    // 402 — CARD DECLINED
    // ==================================================================

    @Test
    void placeOrder_returns402_cardDeclined() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new PaymentDeclinedException());

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.message").value("Payment declined"));
    }


    // ==================================================================
    // 403 — ADDRESS FORBIDDEN
    // ==================================================================

    @Test
    void placeOrder_returns403_addressForbidden() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new AddressAccessForbiddenException());

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }


    // ==================================================================
    // 404 — ADDRESS NOT FOUND
    // ==================================================================

    @Test
    void placeOrder_returns404_addressNotFound() throws Exception {
        when(orderService.placeOrder(eq(1L), any(PaymentRequest.class)))
                .thenThrow(new AddressNotFoundException(10L));

        mockMvc.perform(post("/api/orders")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }


    // ==================================================================
    // FEAT-10 — GET /api/orders and GET /api/orders/{id}
    // ==================================================================

    @Test
    void listOrders_returns200() throws Exception {
        when(orderService.getOrders(1L)).thenReturn(List.of(paidResponse()));

        mockMvc.perform(get("/api/orders")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(42));
    }

    @Test
    void listOrders_returns200_empty() throws Exception {
        when(orderService.getOrders(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listOrders_returns401_noJwt() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrder_returns200() throws Exception {
        when(orderService.getOrderById(1L, 42L)).thenReturn(paidResponse());

        mockMvc.perform(get("/api/orders/42")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void getOrder_returns401_noJwt() throws Exception {
        mockMvc.perform(get("/api/orders/42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrder_returns403_wrongOwner() throws Exception {
        when(orderService.getOrderById(eq(1L), eq(42L)))
                .thenThrow(new OrderAccessForbiddenException());

        mockMvc.perform(get("/api/orders/42")
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrder_returns404_notFound() throws Exception {
        when(orderService.getOrderById(eq(1L), eq(99L)))
                .thenThrow(new OrderNotFoundException());

        mockMvc.perform(get("/api/orders/99")
                        .with(authentication(userAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }
}
