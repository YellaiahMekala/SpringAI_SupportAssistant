package com.springai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** CallAdvisor: rejects oversized prompts before the model call (JTokkit estimate). */
@Component
public class TokenBudgetAdvisor implements CallAdvisor {

    static final String REJECTION_MESSAGE =
            "Your request is too long to process. Please shorten it and try again.";

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetAdvisor.class);

    private final JTokkitTokenCountEstimator estimator = new JTokkitTokenCountEstimator();
    private final int maxInputTokens;

    public TokenBudgetAdvisor(@Value("${app.token-budget.max-input-tokens:2000}") int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        int estimated = estimateInputTokens(request);
        if (estimated > maxInputTokens) {
            log.warn("Rejected request before model call: estimated {} input tokens exceeds budget {}",
                    estimated, maxInputTokens);
            return rejectedResponse();
        }
        log.debug("Token budget OK: estimated {} input tokens (budget {})", estimated, maxInputTokens);
        return chain.nextCall(request);
    }

    /** Sums the local token estimate across every message in the prompt (system and user). */
    int estimateInputTokens(ChatClientRequest request) {
        int total = 0;
        for (Message message : request.prompt().getInstructions()) {
            String text = message.getText();
            if (text != null) {
                total += estimator.estimate(text);
            }
        }
        return total;
    }

    private ChatClientResponse rejectedResponse() {
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(REJECTION_MESSAGE))));
        return new ChatClientResponse(response, Map.of());
    }

    @Override
    public String getName() {
        return "TokenBudgetAdvisor";
    }

    @Override
    public int getOrder() {
        // Runs first: before SafeGuard (1) and Audit (2), so an over-budget request is rejected
        // before any later advisor sees it.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
