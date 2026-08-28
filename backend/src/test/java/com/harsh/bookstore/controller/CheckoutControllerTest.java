package com.harsh.bookstore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.BasketItemDto;
import com.harsh.bookstore.dto.CheckoutSummaryResponse;
import com.harsh.bookstore.dto.DeliveryAddressDto;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.CheckoutService;
import com.harsh.bookstore.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for CheckoutController.
 * Same authentication strategy as AddressControllerTest.
 */
@WebMvcTest(value = CheckoutController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CheckoutService checkoutService;

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

    private CheckoutSummaryResponse summaryResponse() {
        BasketItemDto item = new BasketItemDto();
        item.setBookId(1L);
        item.setTitle("Clean Code");
        item.setAuthor("Robert C. Martin");
        item.setUnitPrice(new BigDecimal("599.00"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("599.00"));

        DeliveryAddressDto addr = new DeliveryAddressDto();
        addr.setId(10L);
        addr.setRecipientName("Test User");
        addr.setPhoneNumber("9876543210");
        addr.setLine1("1 Test Street");
        addr.setCity("Mumbai");
        addr.setState("Maharashtra");
        addr.setPincode("400001");

        CheckoutSummaryResponse r = new CheckoutSummaryResponse();
        r.setItems(List.of(item));
        r.setBasketTotal(new BigDecimal("599.00"));
        r.setDeliveryCharge(BigDecimal.ZERO);
        r.setEstimatedDeliveryDate("2025-08-21");
        r.setDeliveryAddress(addr);
        return r;
    }


    // ==================================================================
    // GET /api/checkout/summary
    // ==================================================================

    @Test
    void getCheckoutSummary_returns200() throws Exception {
        when(checkoutService.getCheckoutSummary(eq(1L), eq(10L)))
                .thenReturn(summaryResponse());

        mockMvc.perform(get("/api/checkout/summary")
                        .param("addressId", "10")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCharge").value(0))
                .andExpect(jsonPath("$.estimatedDeliveryDate").value("2025-08-21"))
                .andExpect(jsonPath("$.deliveryAddress.id").value(10))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.basketTotal").value(599.00));
    }

    @Test
    void getCheckoutSummary_returns400_missingAddressId() throws Exception {
        // No addressId param → Spring MVC returns 400 before service is called
        mockMvc.perform(get("/api/checkout/summary")
                        .with(authentication(userAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCheckoutSummary_returns400_emptyBasket() throws Exception {
        when(checkoutService.getCheckoutSummary(eq(1L), eq(10L)))
                .thenThrow(new IllegalArgumentException("Basket is empty"));

        mockMvc.perform(get("/api/checkout/summary")
                        .param("addressId", "10")
                        .with(authentication(userAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Basket is empty"));
    }

    @Test
    void getCheckoutSummary_returns403() throws Exception {
        when(checkoutService.getCheckoutSummary(eq(1L), eq(10L)))
                .thenThrow(new AddressAccessForbiddenException());

        mockMvc.perform(get("/api/checkout/summary")
                        .param("addressId", "10")
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You do not have permission to access this address"));
    }

    @Test
    void getCheckoutSummary_returns404() throws Exception {
        when(checkoutService.getCheckoutSummary(eq(1L), eq(10L)))
                .thenThrow(new AddressNotFoundException(10L));

        mockMvc.perform(get("/api/checkout/summary")
                        .param("addressId", "10")
                        .with(authentication(userAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found: 10"));
    }

    @Test
    void getCheckoutSummary_returns401_noJwt() throws Exception {
        mockMvc.perform(get("/api/checkout/summary")
                        .param("addressId", "10"))
                .andExpect(status().isUnauthorized());
    }
}
