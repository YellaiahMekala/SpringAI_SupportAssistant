package com.springai.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolve or create customer row for the authenticated subject. */
@Component
public class CustomerAccounts {

    private final JdbcTemplate jdbc;

    public CustomerAccounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long ensure(Authentication authentication) {
        String subject = authentication.getName();
        String displayName = displayNameFrom(authentication, subject);
        Long id = jdbc.queryForObject("""
                INSERT INTO customers (keycloak_subject, display_name) VALUES (?, ?)
                ON CONFLICT (keycloak_subject) DO UPDATE SET keycloak_subject = EXCLUDED.keycloak_subject
                RETURNING id
                """, Long.class, subject, displayName);
        if (id == null) {
            throw new IllegalStateException("Could not resolve an account for the caller");
        }
        return id;
    }

    private static String displayNameFrom(Authentication authentication, String subject) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String preferred = jwt.getClaimAsString("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred.length() > 120 ? preferred.substring(0, 120) : preferred;
            }
        }
        return "user-" + subject.substring(0, Math.min(8, subject.length()));
    }
}
