package com.springai.service;

import com.springai.dto.TripPlan;
import com.springai.governance.ModelAnswer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Structured-output trip planning samples (call via GovernedCalls). */
@Service
public class TripPlannerService {

    private final ChatClient chatClient;

    @Value("classpath:prompts/trip-guide-template.st")
    private Resource tripGuideTemplate;

    public TripPlannerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ModelAnswer<TripPlan> getTripPlan(String message) {
        ResponseEntity<ChatResponse, TripPlan> result = chatClient
                .prompt()
                .user(message)
                .call()
                .responseEntity(new BeanOutputConverter<>(TripPlan.class));
        return ModelAnswer.of(result.entity(), result.response());
    }

    public ModelAnswer<List<String>> getBestSpots(String message) {
        ResponseEntity<ChatResponse, List<String>> result = chatClient
                .prompt()
                .system(tripGuideTemplate)
                .user(message)
                .call()
                .responseEntity(new ListOutputConverter());
        return ModelAnswer.of(result.entity(), result.response());
    }

    public ModelAnswer<Map<String, Object>> getTripGuide(String message) {
        ResponseEntity<ChatResponse, Map<String, Object>> result = chatClient
                .prompt()
                .system(tripGuideTemplate)
                .user(message)
                .call()
                .responseEntity(new MapOutputConverter());
        return ModelAnswer.of(result.entity(), result.response());
    }

    public ModelAnswer<List<TripPlan>> getCompleteTrip(String message) {
        ResponseEntity<ChatResponse, List<TripPlan>> result = chatClient
                .prompt()
                .user(message)
                .call()
                .responseEntity(new ParameterizedTypeReference<List<TripPlan>>() {
                });
        return ModelAnswer.of(result.entity(), result.response());
    }
}
