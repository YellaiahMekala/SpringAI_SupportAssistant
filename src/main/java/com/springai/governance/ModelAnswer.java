package com.springai.governance;

import org.springframework.ai.chat.model.ChatResponse;

/** Model call result for settlement (value, usage, optional output text for local token estimate). */
public record ModelAnswer<T>(T value, ModelUsage usage, String outputText) {

    public static ModelAnswer<String> ofText(ChatResponse response) {
        String text = textOf(response);
        return new ModelAnswer<>(text, ModelUsage.from(response), text);
    }

    public static <T> ModelAnswer<T> of(T value, ChatResponse response) {
        return new ModelAnswer<>(value, ModelUsage.from(response), textOf(response));
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
