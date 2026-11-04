package com.springai.order;

import java.time.LocalDate;

/** One line of an order list: no events, so listing never loads them. */
public record OrderSummary(String orderRef, OrderStatus status, LocalDate promisedDate) {

    static OrderSummary from(SupportOrder order) {
        return new OrderSummary(order.getOrderRef(), order.getStatus(), order.getPromisedDate());
    }
}
