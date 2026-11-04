package com.springai.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Order DTO without customer identifiers. */
public record OrderResponse(String orderRef, OrderStatus status, LocalDate promisedDate, List<Event> events) {

    public record Event(String eventType, Instant occurredAt) {
    }

    static OrderResponse from(SupportOrder order) {
        List<Event> events = order.getEvents().stream()
                .map(event -> new Event(event.getEventType(), event.getOccurredAt()))
                .toList();
        return new OrderResponse(order.getOrderRef(), order.getStatus(), order.getPromisedDate(), events);
    }
}
