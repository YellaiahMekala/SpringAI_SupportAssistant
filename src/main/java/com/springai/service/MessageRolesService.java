package com.springai.service;

import com.springai.governance.ModelAnswer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/** Demonstrates system/user message roles. Callers must go through {@code GovernedCalls}. */
@Service
public class MessageRolesService {

    private static final String CLAIM_DETAILS = """
            Claim Details:
            Policy: BASIC
            Max Coverage: 20000
            Claim Amount: 50000
            """;

    private final ChatClient chatClient;

    public MessageRolesService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /** System and user messages as separate roles, so customer text cannot rewrite the instructions. */
    public ModelAnswer<String> checkPolicy(String message) {
        SystemMessage systemMessage = new SystemMessage("""
                You are an insurance assistant.
                You shouldn't reveal internal policy details,
                 or internal reasoning.
                Respond ONLY with a short, customer-safe message.
                """);
        UserMessage userMessage = new UserMessage("""
                %S
                Customer says: %s
                """.formatted(CLAIM_DETAILS, message));

        ChatResponse response = chatClient.prompt(new Prompt(List.of(systemMessage, userMessage)))
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }

    /** The same claim data with a different system role, to show how the role changes the answer. */
    public ModelAnswer<String> checkInsurancePolicy(String message) {
        ChatResponse response = chatClient.prompt()
                .system("""
                        You are an IT support assistant, "should help with system support issues to users""")
                .user("""
                         %S
                        Customer says:
                         %s """.formatted(CLAIM_DETAILS, message))
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }

    public ModelAnswer<String> guideMe(String topic, String level, int points) {
        ChatResponse response = chatClient.prompt()
                .system("You are a tech stack assistant. Give the best answer.")
                .user("Explain " + topic + " at " + level + " level in " + points + " points.")
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }
}
