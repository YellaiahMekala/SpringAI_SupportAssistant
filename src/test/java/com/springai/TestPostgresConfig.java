package com.springai;

import com.springai.testsupport.HashingEmbeddingProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same PostgreSQL 17 + pgvector image used by docker-compose, for integration tests.
 * Spring Boot wires the container's connection details into the datasource automatically.
 *
 * It also replaces the embedding model with a deterministic fake, so no test needs a model server or network.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPostgresConfig {

    static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(IMAGE);
    }

    /**
     * One shared fake per context; tests can inspect how often it was called or make it fail. It is an
     * EmbeddingProvider, so it is also what the application receives, in place of the real one.
     */
    @Bean
    @Primary
    HashingEmbeddingProvider hashingEmbeddingProvider() {
        return new HashingEmbeddingProvider();
    }
}
