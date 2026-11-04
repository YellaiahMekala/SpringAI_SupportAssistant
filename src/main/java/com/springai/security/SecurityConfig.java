package com.springai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** JWT resource server; deny-by-default except health/info. */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            KeycloakJwtAuthenticationConverter jwtConverter,
                                            ProblemJsonSecurityHandlers problemHandlers,
                                            @org.springframework.beans.factory.annotation.Value("${app.metrics.public-scrape:false}") boolean publicScrape) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Aggregate counters only, no user data. Closed by default; local Compose opens it for Prometheus.
                        .requestMatchers("/actuator/prometheus").access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(publicScrape))
                        // Document management is for support agents only (listed before the broader rule below).
                        .requestMatchers("/api/v1/documents", "/api/v1/documents/**").hasRole("SUPPORT_AGENT")
                        // Every other API path needs a recognised role. Ownership of orders is enforced in the service.
                        .requestMatchers("/api/**", "/multi-model/**").hasAnyRole("CUSTOMER", "SUPPORT_AGENT")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
