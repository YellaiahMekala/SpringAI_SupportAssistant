package com.springai.order;

import com.springai.TestPostgresConfig;
import com.springai.security.KeycloakJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 acceptance: deny by default, role checks, and order ownership (IDOR protection), against a real
 * PostgreSQL container with the demo seed data. Tokens are built with the claims Keycloak issues and pass through
 * the real role converter, so the test covers the application's own mapping. Signature validation by the real
 * Keycloak is covered by a live check, not here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@Testcontainers(disabledWithoutDocker = true)
class OrderSecurityTest {

    private static final String ALICE = "11111111-1111-4111-8111-111111111111";
    private static final String BOB = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private KeycloakJwtAuthenticationConverter converter;

    // ---- deny by default ----

    @Test
    void ordersRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/orders/ORD-1001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void existingChatEndpointsNowRequireAToken() throws Exception {
        mvc.perform(get("/api/chat/message").param("message", "hello")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/order-support")
                        .param("customerName", "a").param("orderId", "1").param("customerMessage", "hi"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trips/plan-trip").param("message", "Paris")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublicButMetricsAreNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutARecognisedRoleCannotUseOrders() throws Exception {
        mvc.perform(get("/api/v1/orders/ORD-1001").with(token(ALICE, "offline_access", "admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders").with(token(ALICE)))
                .andExpect(status().isForbidden());
    }

    // ---- customer: own orders only ----

    @Test
    void aCustomerReadsTheirOwnOrderWithItsMilestones() throws Exception {
        mvc.perform(get("/api/v1/orders/ORD-1001").with(token(ALICE, "customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderRef").value("ORD-1001"))
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.promisedDate").value("2026-11-10"))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[0].eventType").value("PLACED"))
                .andExpect(jsonPath("$.events[2].eventType").value("DELAYED"));
    }

    @Test
    void aCustomerCannotReadAnotherCustomersOrder() throws Exception {
        mvc.perform(get("/api/v1/orders/ORD-2001").with(token(ALICE, "customer")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/ORD-1001").with(token(BOB, "customer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void notOwnedAndNonexistentOrdersLookIdentical() throws Exception {
        // Same status and same body, so a caller cannot tell "exists but not yours" from "does not exist".
        var notOwned = mvc.perform(get("/api/v1/orders/ORD-2001").with(token(ALICE, "customer")))
                .andReturn().getResponse();
        var missing = mvc.perform(get("/api/v1/orders/ORD-9999").with(token(ALICE, "customer")))
                .andReturn().getResponse();

        org.assertj.core.api.Assertions.assertThat(notOwned.getStatus()).isEqualTo(missing.getStatus()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(normalise(notOwned.getContentAsString()))
                .isEqualTo(normalise(missing.getContentAsString()));
    }

    @Test
    void aCustomerListsOnlyTheirOwnOrders() throws Exception {
        mvc.perform(get("/api/v1/orders").with(token(ALICE, "customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].orderRef").value(org.hamcrest.Matchers.containsInAnyOrder("ORD-1001", "ORD-1002")));
        mvc.perform(get("/api/v1/orders").with(token(BOB, "customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderRef").value("ORD-2001"));
    }

    @Test
    void aCustomerWithoutAnAccountSeesNothing() throws Exception {
        String stranger = "99999999-9999-4999-8999-999999999999";
        mvc.perform(get("/api/v1/orders").with(token(stranger, "customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/orders/ORD-1001").with(token(stranger, "customer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCustomerCannotGainAgentAccessByAskingForIt() throws Exception {
        // Identity comes from the validated token only. No request parameter can change it.
        mvc.perform(get("/api/v1/orders/ORD-2001").param("role", "support_agent").param("customer", BOB)
                        .with(token(ALICE, "customer")))
                .andExpect(status().isNotFound());
    }

    // ---- support agent: any order ----

    @Test
    void aSupportAgentReadsAnyCustomersOrder() throws Exception {
        String agent = "33333333-3333-4333-8333-333333333333";
        mvc.perform(get("/api/v1/orders/ORD-1001").with(token(agent, "support_agent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderRef").value("ORD-1001"));
        mvc.perform(get("/api/v1/orders/ORD-2001").with(token(agent, "support_agent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderRef").value("ORD-2001"));
    }

    @Test
    void aSupportAgentListsOrdersAcrossCustomers() throws Exception {
        String agent = "33333333-3333-4333-8333-333333333333";
        mvc.perform(get("/api/v1/orders").with(token(agent, "support_agent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderRef")
                        .value(org.hamcrest.Matchers.hasItems("ORD-1001", "ORD-1002", "ORD-2001")));
    }

    // ---- who am I ----

    @Test
    void meReportsTheSubjectRolesAndDisplayName() throws Exception {
        mvc.perform(get("/api/v1/me").with(token(ALICE, "customer", "offline_access")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(ALICE))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.displayName").value("Alice Demo"));
    }

    // ---- helpers ----

    /** A request carrying a token built like Keycloak's, converted by the application's real converter. */
    private RequestPostProcessor token(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return authentication(converter.convert(jwt));
    }

    private static String normalise(String body) {
        // Drop the fields that legitimately differ between two responses: the request id, the echoed request path
        // (the caller's own input) and any timestamp. What is left is status, title and detail.
        return body.replaceAll("\"requestId\":\"[^\"]*\"", "")
                .replaceAll("\"instance\":\"[^\"]*\"", "")
                .replaceAll("\"timestamp\":\"[^\"]*\"", "")
                .replaceAll("\"path\":\"[^\"]*\"", "");
    }
}
