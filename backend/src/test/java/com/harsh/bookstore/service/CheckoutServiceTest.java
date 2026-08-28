package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.BasketItemDto;
import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.dto.CheckoutSummaryResponse;
import com.harsh.bookstore.entity.DeliveryAddress;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.repository.DeliveryAddressRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


/**
 * Unit tests for CheckoutService.
 * BasketService is mocked — these tests exercise CheckoutService logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private BasketService basketService;

    @Mock
    private DeliveryAddressRepository addressRepository;

    private CheckoutService checkoutService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ADDRESS_ID = 10L;


    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutService(basketService, addressRepository);
    }


    // ==================================================================
    // HELPERS
    // ==================================================================

    private BasketResponse basketWithTotal(BigDecimal total) {
        BasketItemDto item = new BasketItemDto();
        item.setBookId(1L);
        item.setTitle("Clean Code");
        item.setAuthor("Robert C. Martin");
        item.setUnitPrice(total);
        item.setQuantity(1);
        item.setLineTotal(total);

        BasketResponse r = new BasketResponse();
        r.setItems(List.of(item));
        r.setTotalItems(1);
        r.setBasketTotal(total);
        return r;
    }

    private BasketResponse emptyBasket() {
        BasketResponse r = new BasketResponse();
        r.setItems(List.of());
        r.setTotalItems(0);
        r.setBasketTotal(BigDecimal.ZERO);
        return r;
    }

    private DeliveryAddress address(Long userId) {
        DeliveryAddress a = new DeliveryAddress();
        a.setId(ADDRESS_ID);
        a.setUserId(userId);
        a.setRecipientName("Test User");
        a.setPhoneNumber("9876543210");
        a.setLine1("1 Test Street");
        a.setCity("Mumbai");
        a.setState("Maharashtra");
        a.setPincode("400001");
        return a;
    }


    // ==================================================================
    // getCheckoutSummary — delivery charge
    // ==================================================================

    @Test
    void getCheckoutSummary_freeDelivery() {
        when(basketService.getBasket(USER_ID, null))
                .thenReturn(basketWithTotal(new BigDecimal("500.00")));
        when(addressRepository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(USER_ID)));

        CheckoutSummaryResponse response =
                checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID);

        assertThat(response.getDeliveryCharge())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getDeliveryAddress().getId()).isEqualTo(ADDRESS_ID);
    }

    @Test
    void getCheckoutSummary_paidDelivery() {
        when(basketService.getBasket(USER_ID, null))
                .thenReturn(basketWithTotal(new BigDecimal("499.99")));
        when(addressRepository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(USER_ID)));

        CheckoutSummaryResponse response =
                checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID);

        assertThat(response.getDeliveryCharge())
                .isEqualByComparingTo(new BigDecimal("50.00"));
    }


    // ==================================================================
    // getCheckoutSummary — error paths
    // ==================================================================

    @Test
    void getCheckoutSummary_emptyBasket_throws() {
        when(basketService.getBasket(USER_ID, null)).thenReturn(emptyBasket());

        assertThatThrownBy(() -> checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Basket is empty");
    }

    @Test
    void getCheckoutSummary_addressNotFound_throws() {
        when(basketService.getBasket(USER_ID, null))
                .thenReturn(basketWithTotal(new BigDecimal("300.00")));
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessageContaining(String.valueOf(ADDRESS_ID));
    }

    @Test
    void getCheckoutSummary_addressForbidden_throws() {
        when(basketService.getBasket(USER_ID, null))
                .thenReturn(basketWithTotal(new BigDecimal("300.00")));
        when(addressRepository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(OTHER_USER_ID))); // wrong owner

        assertThatThrownBy(() -> checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID))
                .isInstanceOf(AddressAccessForbiddenException.class);
    }


    // ==================================================================
    // getCheckoutSummary — date and items pass-through
    // ==================================================================

    @Test
    void getCheckoutSummary_estimatedDeliveryDate() {
        when(basketService.getBasket(USER_ID, null))
                .thenReturn(basketWithTotal(new BigDecimal("300.00")));
        when(addressRepository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(USER_ID)));

        CheckoutSummaryResponse response =
                checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID);

        assertThat(response.getEstimatedDeliveryDate())
                .isEqualTo(LocalDate.now().plusDays(3).toString());
    }

    @Test
    void getCheckoutSummary_itemsAndTotals() {
        BasketResponse basket = basketWithTotal(new BigDecimal("598.00"));
        when(basketService.getBasket(USER_ID, null)).thenReturn(basket);
        when(addressRepository.findById(ADDRESS_ID))
                .thenReturn(Optional.of(address(USER_ID)));

        CheckoutSummaryResponse response =
                checkoutService.getCheckoutSummary(USER_ID, ADDRESS_ID);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getBasketTotal())
                .isEqualByComparingTo(new BigDecimal("598.00"));
    }
}
