package com.harsh.bookstore.entity;

/**
 * OrderStatus — possible states for a placed order.
 *
 * Stored as STRING in the database (@Enumerated(EnumType.STRING)) so that column
 * values are the readable strings "PAID" or "CANCELLED", not ordinal integers.
 * Ordinal storage is fragile — inserting a new value between existing ones silently
 * corrupts all existing rows if the declaration order changes.
 *
 * CANCELLED is defined here for completeness (used by FEAT-12 order cancellation).
 * FEAT-08 only ever sets status to PAID.
 */
public enum OrderStatus {
    PAID,
    CANCELLED
}
