package com.springai.controller;

import com.springai.order.CustomerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Who the API thinks the caller is. Useful for the UI and for checking token and role mapping. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CustomerRepository customers;

    public MeController(CustomerRepository customers) {
        this.customers = customers;
    }

    public record Me(String subject, List<String> roles, String displayName) {
    }

    @GetMapping
    public Me me(Authentication authentication) {
        String subject = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        String displayName = customers.findByKeycloakSubject(subject)
                .map(customer -> customer.getDisplayName())
                .orElse(null);
        return new Me(subject, roles, displayName);
    }
}
