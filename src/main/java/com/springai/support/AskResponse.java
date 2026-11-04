package com.springai.support;

import java.util.List;

/** Support answer payload (status, text, usage, validated citations). */
public record AskResponse(Status status, String answer, String requestId, Usage usage, List<Citation> citations) {

    public enum Status { ANSWER, ABSTAIN, CLARIFY, DENY }

    /** Token counts for this call. {@code estimated} is true when they are local estimates, not provider counts. */
    public record Usage(int inputTokens, int outputTokens, boolean estimated, String costStatus) {
    }
}
