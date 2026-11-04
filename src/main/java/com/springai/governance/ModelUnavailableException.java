package com.springai.governance;

import java.time.Duration;

/** The circuit breaker is open: the model has been failing, so this call was not even tried. */
public class ModelUnavailableException extends RuntimeException {

    private final Duration retryAfter;

    public ModelUnavailableException(Duration retryAfter) {
        super("The language model is temporarily unavailable");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
