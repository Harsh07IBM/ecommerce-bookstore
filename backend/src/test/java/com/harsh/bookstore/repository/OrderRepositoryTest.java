package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Order;
import com.harsh.bookstore.entity.OrderStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * OrderRepositoryTest — @DataJpaTest slice for OrderRepository.
 *
 * Verifies that the derived query findAllByUserId returns correct results.
 */
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;


    // ==================================================================
    // HELPERS
    // ==================================================================

    private Order buildOrder(Long userId) {
        Order o = new Order();
        o.setUserId(userId);
        o.setStatus(OrderStatus.PAID);
        o.setBasketTotal(new BigDecimal("299.00"));
        o.setDeliveryCharge(new BigDecimal("50.00"));
        o.setTotalAmount(new BigDecimal("349.00"));
        o.setEstimatedDeliveryDate("2025-09-01");
        o.setRecipientName("Test User");
        o.setPhoneNumber("9876543210");
        o.setLine1("1 Main St");
        o.setCity("Mumbai");
        o.setState("Maharashtra");
        o.setPincode("400001");
        return o;
    }


    // ==================================================================
    // TESTS
    // ==================================================================

    @Test
    void findAllByUserId_returnsOrders() {
        orderRepository.save(buildOrder(1L));
        orderRepository.save(buildOrder(1L));
        orderRepository.save(buildOrder(2L));

        List<Order> result = orderRepository.findAllByUserId(1L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(o -> o.getUserId().equals(1L));
    }

    @Test
    void findAllByUserId_returnsEmpty_whenNone() {
        List<Order> result = orderRepository.findAllByUserId(999L);
        assertThat(result).isEmpty();
    }
}
