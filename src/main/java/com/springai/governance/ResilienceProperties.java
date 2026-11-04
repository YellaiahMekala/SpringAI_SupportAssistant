package com.springai.governance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Circuit-breaker settings for calls to the language model. The defaults are starting points, not tuned values. */
@Validated
@ConfigurationProperties(prefix = "app.resilience")
public record ResilienceProperties(
        /* Percentage of failed calls in the window that opens the circuit. */
        @DefaultValue("50") @Min(1) @Max(100) int failureRateThreshold,
        /* How many recent calls are looked at. */
        @DefaultValue("10") @Min(2) int slidingWindowSize,
        /* The circuit cannot open before this many calls were seen. */
        @DefaultValue("5") @Min(1) int minimumCalls,
        /* How long the circuit stays open before it lets trial calls through. */
        @DefaultValue("30s") Duration openFor,
        /* Trial calls allowed while half open. */
        @DefaultValue("2") @Min(1) int halfOpenCalls) {

    public ResilienceProperties {
        if (minimumCalls > slidingWindowSize) {
            throw new IllegalArgumentException("app.resilience.minimum-calls must not exceed sliding-window-size");
        }
    }

    public static ResilienceProperties defaults() {
        return new ResilienceProperties(50, 10, 5, Duration.ofSeconds(30), 2);
    }
}
