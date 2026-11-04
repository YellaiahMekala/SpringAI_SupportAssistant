package com.springai.persistence;

import com.springai.TestPostgresConfig;
import com.springai.budget.BudgetLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1 acceptance: migrations apply, vector columns have the provider dimensions,
 * constraints reject bad rows, and the budget never overspends, even under concurrency.
 * Runs against a real PostgreSQL 17 + pgvector container; skipped when Docker is unavailable.
 */
@SpringBootTest
@Import(TestPostgresConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class PersistenceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BudgetLedger budget;

    @Test
    void flywayAppliedMigrationV1() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void embeddingColumnsHaveTheDimensionOfTheirProvider() {
        assertThat(vectorDimension("chunk_embeddings_ollama")).isEqualTo(768);   // nomic-embed-text
        assertThat(vectorDimension("chunk_embeddings_openai")).isEqualTo(1536);  // text-embedding-3-small
    }

    @Test
    @Transactional
    void embeddingOfTheWrongDimensionIsRejected() {
        long chunkId = seedChunk();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO chunk_embeddings_ollama (chunk_id, embedding) VALUES (?, '[0.1,0.2,0.3]'::vector)", chunkId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void orderStatusOutsideTheAllowedSetIsRejected() {
        long customerId = seedCustomer();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO orders (customer_id, order_ref, status) VALUES (?, ?, 'LOST')", customerId, "ORD-" + unique()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void budgetAllowsSpendUpToTheLimitAndRejectsMore() {
        long customerId = seedCustomer();

        assertThat(budget.tryConsume(customerId, DAY, 60, 100)).isTrue();
        assertThat(budget.tryConsume(customerId, DAY, 50, 100)).isFalse();   // would reach 110
        assertThat(budget.tryConsume(customerId, DAY, 40, 100)).isTrue();    // reaches exactly 100
        assertThat(budget.tryConsume(customerId, DAY, 1, 100)).isFalse();

        assertThat(budget.tokensUsed(customerId, DAY)).isEqualTo(100);
    }

    @Test
    void concurrentSpendNeverExceedsTheLimit() throws Exception {
        long customerId = seedCustomer();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> spend = () -> budget.tryConsume(customerId, DAY, 30, 100);
                results.add(pool.submit(spend));
            }
            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            // 100 / 30 allows exactly 3 spends (90 tokens). Any more would be an overspend.
            assertThat(accepted).isEqualTo(3);
            assertThat(budget.tokensUsed(customerId, DAY)).isEqualTo(90);
        } finally {
            pool.shutdownNow();
        }
    }

    private int vectorDimension(String table) {
        // pgvector stores the declared dimension in the column's type modifier.
        Integer dim = jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute WHERE attrelid = ?::regclass AND attname = 'embedding'",
                Integer.class, table);
        return dim == null ? -1 : dim;
    }

    private long seedCustomer() {
        return jdbc.queryForObject(
                "INSERT INTO customers (keycloak_subject, display_name) VALUES (?, 'Test customer') RETURNING id",
                Long.class, "sub-" + unique());
    }

    private long seedChunk() {
        // source_hash is CHAR(64): two UUIDs without dashes give exactly 64 hex characters.
        Long documentId = jdbc.queryForObject(
                "INSERT INTO documents (title, source_hash) VALUES ('Test doc', ?) RETURNING id",
                Long.class, unique() + unique());
        return jdbc.queryForObject(
                "INSERT INTO document_chunks (document_id, chunk_index, text) VALUES (?, 0, 'text') RETURNING id",
                Long.class, documentId);
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
