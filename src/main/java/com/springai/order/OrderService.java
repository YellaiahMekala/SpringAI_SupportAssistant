package com.springai.order;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Order lookup with ownership enforced in the query (identical 404 for missing vs other customer's order). */
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderRef, CallerIdentity caller) {
        SupportOrder order = (caller.supportAgent()
                ? orders.findByOrderRef(orderRef)
                : orders.findByOrderRefAndCustomerKeycloakSubject(orderRef, caller.subject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> listOrders(CallerIdentity caller) {
        List<SupportOrder> found = caller.supportAgent()
                ? orders.findTop50ByOrderByCreatedAtDesc()
                : orders.findByCustomerKeycloakSubjectOrderByCreatedAtDesc(caller.subject());
        return found.stream().map(OrderSummary::from).toList();
    }
}
