package com.springai.service;

import com.springai.advisor.AuditTokenUsageAdvisor;
import com.springai.advisor.TokenBudgetAdvisor;
import com.springai.governance.ModelAnswer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/** Prompt-template order support with TokenBudgetAdvisor (call via GovernedCalls). */
@Service
public class OrderSupportAISupportAssistantService {
    private final ChatClient chatClient;
    private final TokenBudgetAdvisor tokenBudgetAdvisor;

    @Value("classpath:prompts/order_system_template.st")
    private Resource systemOrderPrompt;
    @Value("classpath:prompts/order_user_template.st")
    private Resource orderUserPrompt;

    @Value("classpath:prompts/order_system_policy_template.st")
    private Resource orderSystemPolicyPrompt;

    public OrderSupportAISupportAssistantService(ChatClient.Builder chatClientBuilder,
                                                 TokenBudgetAdvisor tokenBudgetAdvisor) {
        this.chatClient = chatClientBuilder.build();
        this.tokenBudgetAdvisor = tokenBudgetAdvisor;
    }

    /**
     * Advisor chain: token budget -> usage audit.
     *
     * No request/response logging advisor: it would write the full prompt to the log. No keyword advisor:
     * the sensitive-data policy applied at admission replaced it (it blocked harmless messages such as
     * "I forgot my password").
     */
    public ModelAnswer<String> talkToAISupport(String customerName, String orderId, String customerMessage) {
        ChatResponse response = chatClient
                .prompt()
                .advisors(List.of(tokenBudgetAdvisor, new AuditTokenUsageAdvisor()))
                .system(orderSystemPolicyPrompt)
                .user(promptUserSpec -> promptUserSpec
                        .text(orderUserPrompt)
                        .param("customerName", customerName)
                        .param("orderId", orderId)
                        .param("customerMessage", customerMessage))
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }

    /** Baseline without advisors, kept for comparison with the advisor path. */
    public ModelAnswer<String> assistWithOrderSupport(String customerName, String orderId, String customerMessage) {
        ChatResponse response = chatClient
                .prompt()
                .system(systemOrderPrompt)
                .user(promptUserSpec -> promptUserSpec
                        .text(orderUserPrompt)
                        .param("customerName", customerName)
                        .param("orderId", orderId)
                        .param("customerMessage", customerMessage))
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }
}
