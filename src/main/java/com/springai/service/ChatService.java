package com.springai.service;

import com.springai.governance.ModelAnswer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/** Plain / streaming chat (call via GovernedCalls). */
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ModelAnswer<String> chat(String message) {
        ChatOptions chatOptions = ChatOptions.builder()
                .temperature(0.5)
                .maxTokens(200)
                .topK(50)
                .topP(0.5)
                .frequencyPenalty(0.7)
                .presencePenalty(0.7)
                .stopSequences(List.of("}", "]", "/*"))
                .build();
        ChatResponse response = chatClient
                .prompt(message)
                .options(chatOptions)
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }

    public Flux<String> stream(String message) {
        return chatClient
                .prompt(message)
                .stream()
                .content();
    }
}
