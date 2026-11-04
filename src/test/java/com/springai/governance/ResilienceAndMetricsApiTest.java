package com.springai.governance;

import com.springai.TestPostgresConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.ConnectException;

import static com.springai.testsupport.TestFixtures.modelResponse;
import static com.springai.testsupport.TestFixtures.newSubject;
import static com.springai.testsupport.TestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7: when the model keeps failing the circuit opens, the API answers 503 at once with Retry-After, the model
 * is not called, and the customer's budget is untouched. Also: counters for tokens and rejections, and the
 * Prometheus endpoint (closed unless public scraping is switched on).
 */
@SpringBootTest
@AutoConfigureObservability   // tests switch metric exporters off by default; the scrape test needs Prometheus on
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@TestPropertySource(properties = {
        "app.resilience.sliding-window-size=2", "app.resilience.minimum-calls=2", "app.resilience.open-for=1h",
        "app.metrics.public-scrape=true"})
@Testcontainers(disabledWithoutDocker = true)
class ResilienceAndMetricsApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MeterRegistry registry;

    @MockitoBean
    private OllamaChatModel chatModel;

    @Test
    void whenTheModelKeepsFailingTheCircuitOpensAndTheCustomerIsNotCharged() throws Exception {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenThrow(new ResourceAccessException("down", new ConnectException()));
        String subject = newSubject();

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/chat/message").param("message", "hi").with(token(subject, "customer")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"));
        }
        verify(chatModel, times(2)).call(any(Prompt.class));

        mvc.perform(get("/api/chat/message").param("message", "hi").with(token(subject, "customer")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"))
                .andExpect(header().string("Retry-After", "3600"));

        verify(chatModel, times(2)).call(any(Prompt.class));   // the third call never reached the model
        mvc.perform(get("/api/v1/usage/today").with(token(subject, "customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokensUsed").value(0));
        assertThat(registry.get("support.model.calls").tag("outcome", "rejected").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void tokensAndRejectionsAreCountedAndTheScrapeEndpointShowsThem() throws Exception {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse("Hello", 10, 5));
        String subject = newSubject();
        double before = registry.counter("support.rejections", "code", "SENSITIVE_DATA").count();

        // A request with a one-time code is rejected before the model.
        mvc.perform(get("/api/chat/message").param("message", "my otp is 482913").with(token(subject, "customer")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(registry.counter("support.rejections", "code", "SENSITIVE_DATA").count()).isEqualTo(before + 1);

        String scrape = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(scrape).contains("support_rejections_total").contains("support_model_circuit_state");
        assertThat(scrape).doesNotContain(subject).doesNotContain("482913");
    }
}
