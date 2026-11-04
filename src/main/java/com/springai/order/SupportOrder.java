package com.springai.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** An order owned by one customer. Named SupportOrder to avoid clashing with the SQL keyword. */
@Entity
@Table(name = "orders")
public class SupportOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "order_ref", nullable = false, updatable = false)
    private String orderRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC")
    private List<OrderEvent> events = new ArrayList<>();

    protected SupportOrder() {
        // for JPA
    }

    public String getOrderRef() {
        return orderRef;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDate getPromisedDate() {
        return promisedDate;
    }

    public List<OrderEvent> getEvents() {
        return events;
    }
}
