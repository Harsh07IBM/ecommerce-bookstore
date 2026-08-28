package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * OrderRepository — data access for the Order entity.
 *
 * findAllByUserId is a Spring Data derived query (no @Query needed).
 * It is the query used by FEAT-10 (order history) — declaring it here
 * means the FEAT-10 coding phase requires no repository change.
 *
 * The idx_order_user_id index on the orders table makes this efficient.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUserId(Long userId);
}
