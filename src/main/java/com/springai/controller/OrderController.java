package com.springai.controller;

import com.springai.order.CallerIdentity;
import com.springai.order.OrderResponse;
import com.springai.order.OrderService;
import com.springai.order.OrderSummary;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** The caller's own orders; for a support agent, the most recent orders across customers. */
    @GetMapping
    public List<OrderSummary> list(Authentication authentication) {
        return orderService.listOrders(CallerIdentity.from(authentication));
    }

    /** One order with its milestones. Customers get 404 for orders that are not theirs. */
    @GetMapping("/{orderRef}")
    public OrderResponse get(@PathVariable String orderRef, Authentication authentication) {
        return orderService.getOrder(orderRef, CallerIdentity.from(authentication));
    }
}
