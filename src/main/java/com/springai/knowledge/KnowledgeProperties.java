package com.springai.knowledge;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Settings for ingestion and retrieval, validated at startup. */
@Validated
@ConfigurationProperties(prefix = "app.knowledge")
public record KnowledgeProperties(
        /* Excerpts sent to the model. */
        @DefaultValue("4") @Min(1) @Max(20) int topK,
        /* Minimum cosine similarity for an excerpt to count as evidence. Uncalibrated until measured. */
        @DefaultValue("0.5") @DecimalMin("0.0") @DecimalMax("1.0") double minScore,
        @DefaultValue("1000") @Min(200) int chunkMaxChars,
        @DefaultValue("150") @Min(0) int chunkOverlapChars,
        @DefaultValue("1048576") @Min(1) long maxUploadBytes,
        @DefaultValue("./knowledge") String ingestDir) {

    public KnowledgeProperties {
        if (chunkOverlapChars >= chunkMaxChars) {
            throw new IllegalArgumentException("app.knowledge.chunk-overlap-chars must be smaller than chunk-max-chars");
        }
    }
}
