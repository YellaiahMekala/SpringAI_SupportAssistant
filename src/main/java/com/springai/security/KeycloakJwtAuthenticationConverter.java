package com.springai.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps Keycloak {@code realm_access.roles} to Spring authorities used by this app. */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CUSTOMER = "customer";
    public static final String SUPPORT_AGENT = "support_agent";

    private static final Set<String> KNOWN_ROLES = Set.of(CUSTOMER, SUPPORT_AGENT);

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : realmRoles(jwt)) {
            if (KNOWN_ROLES.contains(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /** Reads {@code realm_access.roles}, tolerating a missing or malformed claim (no roles, never an exception). */
    private static List<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object role : collection) {
            if (role instanceof String text) {
                result.add(text);
            }
        }
        return result;
    }
}
