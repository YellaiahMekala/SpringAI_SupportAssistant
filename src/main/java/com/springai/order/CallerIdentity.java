package com.springai.order;

import com.springai.security.KeycloakJwtAuthenticationConverter;
import org.springframework.security.core.Authentication;

import java.util.Locale;

/** Caller subject and agent flag from the authenticated principal. */
public record CallerIdentity(String subject, boolean supportAgent) {

    private static final String AGENT_AUTHORITY =
            "ROLE_" + KeycloakJwtAuthenticationConverter.SUPPORT_AGENT.toUpperCase(Locale.ROOT);

    public static CallerIdentity from(Authentication authentication) {
        boolean agent = authentication.getAuthorities().stream()
                .anyMatch(authority -> AGENT_AUTHORITY.equals(authority.getAuthority()));
        return new CallerIdentity(authentication.getName(), agent);
    }
}
