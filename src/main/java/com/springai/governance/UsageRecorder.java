package com.springai.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists usage_events (no prompt/answer text). */
@Repository
public class UsageRecorder {

    public enum CostStatus { ESTIMATED, LOCAL_MODEL, NOT_CONFIGURED }

    public record UsageRow(
            long customerId,
            String correlationId,
            String provider,
            String model,
            String operation,
            int estimatedInputTokens,
            int inputTokens,
            int outputTokens,
            boolean reported,
            long estimatedCostMicros,
            CostStatus costStatus) {
    }

    private final JdbcTemplate jdbc;

    public UsageRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UsageRow row) {
        jdbc.update("""
                INSERT INTO usage_events (customer_id, correlation_id, provider, model, operation,
                                          estimated_input_tokens, input_tokens, output_tokens, usage_reported,
                                          estimated_cost_micros, cost_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.customerId(), row.correlationId(), row.provider(), row.model(), row.operation(),
                row.estimatedInputTokens(), row.inputTokens(), row.outputTokens(), row.reported(),
                row.estimatedCostMicros(), row.costStatus().name());
    }
}
