package com.springai.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<SupportOrder, Long> {

    /** Any order by reference. Only for callers already known to be support agents. */
    @EntityGraph(attributePaths = "events")
    Optional<SupportOrder> findByOrderRef(String orderRef);

    /** An order only if it belongs to the customer with this token subject. The ownership check is in the query. */
    @EntityGraph(attributePaths = "events")
    Optional<SupportOrder> findByOrderRefAndCustomerKeycloakSubject(String orderRef, String keycloakSubject);

    /** A customer's own orders, newest first. Events are not fetched here (summaries only). */
    List<SupportOrder> findByCustomerKeycloakSubjectOrderByCreatedAtDesc(String keycloakSubject);

    /** Recent orders across customers, for support agents. Bounded so the result is never unbounded. */
    List<SupportOrder> findTop50ByOrderByCreatedAtDesc();
}
