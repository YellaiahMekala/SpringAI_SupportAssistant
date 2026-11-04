package com.springai.governance;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/** Provider token counts; {@code reported=false} means fall back to local estimates. */
public record ModelUsage(int inputTokens, int outputTokens, boolean reported) {

    public static ModelUsage notReported() {
        return new ModelUsage(0, 0, false);
    }

    public static ModelUsage from(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return notReported();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return notReported();
        }
        int in = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int out = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        return in + out > 0 ? new ModelUsage(in, out, true) : notReported();
    }
}
