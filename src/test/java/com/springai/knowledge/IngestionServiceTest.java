package com.springai.knowledge;

import com.springai.TestPostgresConfig;
import com.springai.governance.GovernanceException;
import com.springai.testsupport.HashingEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ingestion against a real PostgreSQL + pgvector container with the deterministic fake embedder. */
@SpringBootTest
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@Testcontainers(disabledWithoutDocker = true)
class IngestionServiceTest {

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private HashingEmbeddingProvider embeddings;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void resetFake() {
        embeddings.setFailing(false);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void aNewDocumentIsStoredChunkedAndEmbeddedWithTheRightVectorSize() {
        String marker = unique();
        String text = "# Policy " + marker + "\n\nFirst paragraph about quokkas.\n\nSecond paragraph about wombats.";

        IngestionResult result = ingestion.ingest("Policy " + marker, Visibility.PUBLIC, bytes(text));

        assertThat(result.status()).isEqualTo(IngestionResult.Status.CREATED);
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.chunks()).isGreaterThanOrEqualTo(1);
        Integer chunks = jdbc.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id = ?", Integer.class, result.documentId());
        Integer embedded = jdbc.queryForObject("""
                SELECT count(*) FROM chunk_embeddings_ollama e JOIN document_chunks c ON c.id = e.chunk_id
                 WHERE c.document_id = ?""", Integer.class, result.documentId());
        Integer dimensions = jdbc.queryForObject("""
                SELECT vector_dims(e.embedding) FROM chunk_embeddings_ollama e JOIN document_chunks c ON c.id = e.chunk_id
                 WHERE c.document_id = ? LIMIT 1""", Integer.class, result.documentId());
        assertThat(chunks).isEqualTo(result.chunks());
        assertThat(embedded).isEqualTo(result.chunks());
        assertThat(dimensions).isEqualTo(768);
    }

    @Test
    void theSameContentAgainIsADuplicateAndChangesNothing() {
        String text = "Unique text " + unique() + " about quokkas.";
        IngestionResult first = ingestion.ingest("Dup " + unique(), Visibility.PUBLIC, bytes(text));
        int documentsBefore = count("documents");
        int embeddingsBefore = count("chunk_embeddings_ollama");
        embeddings.resetCalls();

        IngestionResult second = ingestion.ingest("Another title", Visibility.PUBLIC, bytes(text));

        assertThat(second.status()).isEqualTo(IngestionResult.Status.DUPLICATE);
        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(count("documents")).isEqualTo(documentsBefore);
        assertThat(count("chunk_embeddings_ollama")).isEqualTo(embeddingsBefore);
        assertThat(embeddings.calls()).as("nothing was missing, so the provider was not called").isZero();
    }

    @Test
    void changedContentUnderTheSameTitleIsANewVersion() {
        String title = "Versioned " + unique();

        IngestionResult one = ingestion.ingest(title, Visibility.PUBLIC, bytes("Version one text " + unique()));
        IngestionResult two = ingestion.ingest(title, Visibility.PUBLIC, bytes("Version two text " + unique()));

        assertThat(one.version()).isEqualTo(1);
        assertThat(two.version()).isEqualTo(2);
        assertThat(two.documentId()).isNotEqualTo(one.documentId());
    }

    @Test
    void embeddingsMissingForTheActiveProviderAreCreatedWhenTheSameContentIsIngestedAgain() {
        String text = "Reindex text " + unique() + " about quokkas.";
        IngestionResult first = ingestion.ingest("Reindex " + unique(), Visibility.PUBLIC, bytes(text));
        // Simulates having switched provider: this provider's table has nothing for the document yet.
        jdbc.update("""
                DELETE FROM chunk_embeddings_ollama WHERE chunk_id IN
                 (SELECT id FROM document_chunks WHERE document_id = ?)""", first.documentId());

        IngestionResult again = ingestion.ingest("Reindex title ignored", Visibility.PUBLIC, bytes(text));

        assertThat(again.status()).isEqualTo(IngestionResult.Status.REINDEXED);
        assertThat(again.documentId()).isEqualTo(first.documentId());
        Integer embedded = jdbc.queryForObject("""
                SELECT count(*) FROM chunk_embeddings_ollama e JOIN document_chunks c ON c.id = e.chunk_id
                 WHERE c.document_id = ?""", Integer.class, first.documentId());
        assertThat(embedded).isEqualTo(first.chunks());
    }

    @Test
    void aDocumentContainingACredentialIsRefusedBeforeAnythingIsStoredOrEmbedded() {
        int documentsBefore = count("documents");
        embeddings.resetCalls();
        String text = "Support notes " + unique() + "\n\nThe admin password is hunter22 for the portal.";

        assertThatThrownBy(() -> ingestion.ingest("Notes", Visibility.PUBLIC, bytes(text)))
                .isInstanceOfSatisfying(GovernanceException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(GovernanceException.Code.SENSITIVE_DATA);
                    assertThat(e.getCategories()).isNotEmpty();
                    assertThat(e.getMessage()).doesNotContain("hunter22");
                });

        assertThat(count("documents")).isEqualTo(documentsBefore);
        assertThat(embeddings.calls()).as("sensitive text must never reach the embedding provider").isZero();
    }

    @Test
    void cardNumbersAreRedactedBeforeTheTextIsStoredOrEmbedded() {
        String text = "Refund note " + unique() + " customer card 4111 1111 1111 1111 was used.";

        IngestionResult result = ingestion.ingest("Card note", Visibility.PUBLIC, bytes(text));

        String stored = jdbc.queryForObject(
                "SELECT string_agg(text, ' ') FROM document_chunks WHERE document_id = ?", String.class,
                result.documentId());
        assertThat(stored).contains("[card ending 1111]").doesNotContain("4111");
    }

    @Test
    void textThatIsNotAcceptableIsRefusedWithAClearReason() {
        assertThatThrownBy(() -> ingestion.ingest("T", Visibility.PUBLIC, new byte[0]))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> ingestion.ingest("T", Visibility.PUBLIC, new byte[] {(byte) 0xC3, 0x28, 0x41}))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> ingestion.ingest("T", Visibility.PUBLIC, bytes("binary\u0000data " + unique())))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("control characters");
        assertThatThrownBy(() -> ingestion.ingest("T", Visibility.PUBLIC, bytes("  \n\n   ")))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("no text");
        assertThatThrownBy(() -> ingestion.ingest("T", Visibility.PUBLIC, new byte[1_048_577]))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("larger");
    }

    @Test
    void theTitleIsValidated() {
        assertThatThrownBy(() -> ingestion.ingest(" ", Visibility.PUBLIC, bytes("text " + unique())))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("title is required");
        assertThatThrownBy(() -> ingestion.ingest(null, Visibility.PUBLIC, bytes("text " + unique())))
                .isInstanceOf(InvalidDocumentException.class);
        assertThatThrownBy(() -> ingestion.ingest("x".repeat(201), Visibility.PUBLIC, bytes("text " + unique())))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("longer than 200");
        assertThatThrownBy(() -> ingestion.ingest("bad\ntitle", Visibility.PUBLIC, bytes("text " + unique())))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("control characters");
    }

    @Test
    void aFailedEmbeddingLeavesNothingBehind() {
        int documentsBefore = count("documents");
        int chunksBefore = count("document_chunks");
        embeddings.setFailing(true);

        assertThatThrownBy(() -> ingestion.ingest("Doomed", Visibility.PUBLIC, bytes("Doomed text " + unique())))
                .isInstanceOf(IllegalStateException.class);

        // The document and chunk rows were written before the embedding call, then rolled back with it.
        assertThat(count("documents")).isEqualTo(documentsBefore);
        assertThat(count("document_chunks")).isEqualTo(chunksBefore);
    }
}
