package com.springai.testsupport;

import com.springai.security.KeycloakJwtAuthenticationConverter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Shared helpers for API tests: tokens built like Keycloak's, and fake model responses. */
public final class TestFixtures {

    public static final String ALICE = "11111111-1111-4111-8111-111111111111";
    public static final String BOB = "22222222-2222-4222-8222-222222222222";
    public static final String AGENT = "33333333-3333-4333-8333-333333333333";

    private static final KeycloakJwtAuthenticationConverter CONVERTER = new KeycloakJwtAuthenticationConverter();

    private TestFixtures() {
    }

    /** A request carrying a token with the given subject and realm roles, converted by the real converter. */
    public static RequestPostProcessor token(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return authentication(CONVERTER.convert(jwt));
    }

    /** A brand-new customer subject, so a test never shares an account or a budget with another test. */
    public static String newSubject() {
        return UUID.randomUUID().toString();
    }

    /** A model response with text and provider-reported token usage. */
    public static ChatResponse modelResponse(String text, int inputTokens, int outputTokens) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(inputTokens, outputTokens)).build());
    }

    /** A model response with text and no usage information (as some providers and streams give). */
    public static ChatResponse modelResponseWithoutUsage(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
