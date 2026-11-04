-- Phase 3: record what was estimated before the call and what the provider reported after it,
-- which operation caused the usage, and how trustworthy the cost figure is.

ALTER TABLE usage_events
    ADD COLUMN operation             VARCHAR(40) NOT NULL DEFAULT 'unknown',
    ADD COLUMN estimated_input_tokens INT         NOT NULL DEFAULT 0 CHECK (estimated_input_tokens >= 0),
    -- true when input_tokens/output_tokens came from the provider; false when they are local estimates
    ADD COLUMN usage_reported        BOOLEAN     NOT NULL DEFAULT FALSE,
    -- ESTIMATED: computed from a configured price table. LOCAL_MODEL: runs locally, no per-token charge.
    -- NOT_CONFIGURED: no price is configured for this model, so no cost figure is claimed.
    ADD COLUMN cost_status           VARCHAR(20) NOT NULL DEFAULT 'NOT_CONFIGURED'
        CHECK (cost_status IN ('ESTIMATED', 'LOCAL_MODEL', 'NOT_CONFIGURED'));

CREATE INDEX idx_usage_events_correlation ON usage_events (correlation_id);
