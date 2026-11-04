package com.springai.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void mapsTheApplicationRolesToSpringAuthorities() {
        var authentication = converter.convert(jwtWith(Map.of("roles", List.of("customer", "support_agent"))));

        assertThat(authorities(authentication)).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_SUPPORT_AGENT");
    }

    @Test
    void ignoresRolesTheApplicationDoesNotDefine() {
        // Keycloak adds default roles such as offline_access; they must never become authorities here.
        var authentication = converter.convert(
                jwtWith(Map.of("roles", List.of("offline_access", "uma_authorization", "admin", "customer"))));

        assertThat(authorities(authentication)).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void aMissingRealmAccessClaimGivesNoAuthorities() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("someone")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(authorities(converter.convert(jwt))).isEmpty();
    }

    @Test
    void aMalformedRolesClaimGivesNoAuthoritiesInsteadOfAnException() {
        assertThat(authorities(converter.convert(jwtWith(Map.of("roles", "customer"))))).isEmpty();
        assertThat(authorities(converter.convert(jwtWith(Map.of("roles", List.of(42, "customer"))))))
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void theAuthenticationNameIsTheTokenSubject() {
        var authentication = converter.convert(jwtWith(Map.of("roles", List.of("customer"))));

        assertThat(authentication.getName()).isEqualTo("subject-1");
    }

    private static Jwt jwtWith(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("subject-1")
                .claim("realm_access", realmAccess)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static List<String> authorities(org.springframework.security.authentication.AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
