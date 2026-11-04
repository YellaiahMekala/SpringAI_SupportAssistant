package com.springai.governance;

import com.springai.TestPostgresConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.springai.testsupport.TestFixtures.modelResponse;
import static com.springai.testsupport.TestFixtures.newSubject;
import static com.springai.testsupport.TestFixtures.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The daily budget with a small limit, so exhaustion can be reached with a few calls. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@TestPropertySource(properties = {
        "app.governance.daily-token-limit=1000",
        "app.governance.reserved-output-tokens=500"
})
@Testcontainers(disabledWithoutDocker = true)
class DailyBudgetApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OllamaChatModel chatModel;

    @BeforeEach
    void stubModel() {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().model("test-model").build());
        // Each call reports 300 + 300 = 600 tokens used.
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse("Hello", 300, 300));
    }

    @Test
    void onceTheDailyLimitIsUsedUpFurtherRequestsAre429WithoutCallingTheModel() throws Exception {
        String subject = newSubject();

        // 1st call: reserves about 500 + input, settles to the real 600.
        mvc.perform(get("/api/chat/message").param("message", "hi").with(token(subject, "customer")))
                .andExpect(status().isOk());

        // 2nd call: 600 already used + a 500+ reservation exceeds 1000, so it is refused before the model.
        mvc.perform(get("/api/chat/message").param("message", "hi").with(token(subject, "customer")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DAILY_BUDGET_EXHAUSTED"))
                .andExpect(jsonPath("$.requestId").exists());

        verify(chatModel, times(1)).call(any(Prompt.class));

        mvc.perform(get("/api/v1/usage/today").with(token(subject, "customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokensUsed").value(600))
                .andExpect(jsonPath("$.dailyLimit").value(1000))
                .andExpect(jsonPath("$.remainingTokens").value(400));
    }

    @Test
    void oneCustomersBudgetDoesNotAffectAnother() throws Exception {
        mvc.perform(get("/api/chat/message").param("message", "hi").with(token(newSubject(), "customer")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat/message").param("message", "hi").with(token(newSubject(), "customer")))
                .andExpect(status().isOk());

        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}
