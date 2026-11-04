package com.springai.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.governance")
public record GovernanceProperties(
        @DefaultValue("500") @Min(0) int reservedOutputTokens,
        /* Safety margin added to the local input estimate, as a percentage (estimates are approximate). */
        @DefaultValue("10") @Min(0) @Max(100) int safetyMarginPercent,
        /* Tokens each account may use per UTC day. */
        @DefaultValue("50000") @Min(1) long dailyTokenLimit,
        @DefaultValue @Valid @NotNull Pricing pricing) {

    /**
     * Prices for cost estimates, in micro-units of the billing currency per one million tokens
     * (so 0.15 per million tokens is 150000). Empty by default: no price is claimed until it is configured
     * from the provider's published price list, together with the "as of" date it was copied on.
     */
    public record Pricing(String asOf, @DefaultValue Map<String, ModelPrice> models) {
    }

    public record ModelPrice(long inputPerMillionMicros, long outputPerMillionMicros) {
    }
}
