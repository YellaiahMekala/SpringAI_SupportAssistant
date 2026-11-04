package com.springai.service;

import com.springai.governance.ModelAnswer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Multi-model chat routing (Ollama wired; call via GovernedCalls). */
@Service
public class MultiModelChatService {

    private final ChatClient chatClient;

    public MultiModelChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ModelAnswer<String> chatWithOllama(String message) {
        return ModelAnswer.ofText(chatClient.prompt(message).call().chatResponse());
    }
}
