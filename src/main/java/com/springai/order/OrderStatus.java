package com.springai.order;

/** Matches the CHECK constraint on {@code orders.status}. */
public enum OrderStatus {
    PLACED, SHIPPED, DELAYED, DELIVERED, CANCELLED
}
