-- Phase 1: relational data, document store, per-provider embeddings, usage and budgets.
-- Money is stored as integer micro-units (1 unit = 0.000001 of the currency) to avoid floating-point drift.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE customers (
    id                BIGSERIAL PRIMARY KEY,
    keycloak_subject  VARCHAR(64)  NOT NULL UNIQUE,
    display_name      VARCHAR(120) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id             BIGSERIAL PRIMARY KEY,
    customer_id    BIGINT      NOT NULL REFERENCES customers (id),
    order_ref      VARCHAR(32) NOT NULL UNIQUE,
    status         VARCHAR(32) NOT NULL CHECK (status IN ('PLACED', 'SHIPPED', 'DELAYED', 'DELIVERED', 'CANCELLED')),
    promised_date  DATE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_customer ON orders (customer_id);

CREATE TABLE order_events (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    event_type   VARCHAR(40)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_order_events_order ON order_events (order_id, occurred_at);

CREATE TABLE documents (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    source_hash  CHAR(64)     NOT NULL UNIQUE,   -- SHA-256 of the source file
    version      INT          NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE document_chunks (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT       NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index  INT          NOT NULL CHECK (chunk_index >= 0),
    page         INT,
    section      VARCHAR(200),
    text         TEXT         NOT NULL,
    UNIQUE (document_id, chunk_index)
);

-- One embedding table per provider, each with its own fixed dimension (decision D4).
-- Ollama nomic-embed-text: 768 dimensions. OpenAI text-embedding-3-small: 1536 dimensions.
CREATE TABLE chunk_embeddings_ollama (
    chunk_id   BIGINT PRIMARY KEY REFERENCES document_chunks (id) ON DELETE CASCADE,
    embedding  vector(768) NOT NULL
);
CREATE INDEX idx_chunk_embeddings_ollama_hnsw ON chunk_embeddings_ollama USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chunk_embeddings_openai (
    chunk_id   BIGINT PRIMARY KEY REFERENCES document_chunks (id) ON DELETE CASCADE,
    embedding  vector(1536) NOT NULL
);
CREATE INDEX idx_chunk_embeddings_openai_hnsw ON chunk_embeddings_openai USING hnsw (embedding vector_cosine_ops);

CREATE TABLE usage_events (
    id                    BIGSERIAL PRIMARY KEY,
    customer_id           BIGINT       NOT NULL REFERENCES customers (id),
    correlation_id        VARCHAR(64)  NOT NULL,
    provider              VARCHAR(20)  NOT NULL,
    model                 VARCHAR(80)  NOT NULL,
    input_tokens          INT          NOT NULL CHECK (input_tokens >= 0),
    output_tokens         INT          NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    estimated_cost_micros BIGINT       NOT NULL DEFAULT 0 CHECK (estimated_cost_micros >= 0),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_usage_events_customer ON usage_events (customer_id, created_at);

-- Daily budget per customer (decision D7). Updated only with a conditional statement, never read-then-write.
CREATE TABLE budget_ledger (
    customer_id   BIGINT NOT NULL REFERENCES customers (id),
    usage_day     DATE   NOT NULL,
    tokens_used   BIGINT NOT NULL DEFAULT 0 CHECK (tokens_used >= 0),
    cost_micros   BIGINT NOT NULL DEFAULT 0 CHECK (cost_micros >= 0),
    PRIMARY KEY (customer_id, usage_day)
);
