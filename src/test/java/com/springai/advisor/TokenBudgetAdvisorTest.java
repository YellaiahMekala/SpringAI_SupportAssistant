package com.springai.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenBudgetAdvisorTest {

    @Test
    void rejectsOversizedRequestWithoutCallingModel() {
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(10);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientRequest request = requestWithText("word ".repeat(500));

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain, never()).nextCall(any());
        assertThat(response.chatResponse().getResult().getOutput().getText())
                .isEqualTo(TokenBudgetAdvisor.REJECTION_MESSAGE);
    }

    @Test
    void passesWithinBudgetRequestToNextAdvisor() {
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(2000);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientRequest request = requestWithText("Where is my order?");
        ChatClientResponse downstream = mock(ChatClientResponse.class);
        when(chain.nextCall(request)).thenReturn(downstream);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        assertThat(response).isSameAs(downstream);
    }

    @Test
    void estimatesTokensAcrossAllMessages() {
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(2000);
        ChatClientRequest request = requestWithText("Where is my order?");

        assertThat(advisor.estimateInputTokens(request)).isPositive();
    }

    private static ChatClientRequest requestWithText(String text) {
        return new ChatClientRequest(new Prompt(new UserMessage(text)), Map.of());
    }
}
