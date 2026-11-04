package com.springai.knowledge;

import com.springai.TestPostgresConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retrieval returns the closest chunks, best first, and applies the access rule inside the query: internal
 * (AGENT_ONLY) documents are never returned to a customer, however the question is phrased.
 */
@SpringBootTest
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@TestPropertySource(properties = {"app.knowledge.min-score=0.3", "app.knowledge.top-k=3"})
@Testcontainers(disabledWithoutDocker = true)
class RetrievalAccessTest {

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private RetrievalService retrieval;

    /** Rare made-up words, so a query matches only the document written for it, whatever else is stored. */
    private static String rare() {
        return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private IngestionResult store(String title, Visibility visibility, String text) {
        return ingestion.ingest(title, visibility, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anInternalDocumentIsNeverReturnedToACustomerButIsReturnedToAnAgent() {
        String secretWord = rare();
        String publicWord = rare();
        String internalTitle = "Internal " + secretWord;
        store(internalTitle, Visibility.AGENT_ONLY, "Internal approval limits " + secretWord + " " + secretWord);
        store("Public " + publicWord, Visibility.PUBLIC, "Public policy " + publicWord + " " + publicWord);

        String question = "approval limits " + secretWord;
        List<RetrievedChunk> forCustomer = retrieval.nearest(question, false);
        List<RetrievedChunk> forAgent = retrieval.nearest(question, true);

        assertThat(forCustomer).extracting(RetrievedChunk::title).doesNotContain(internalTitle);
        assertThat(forAgent.get(0).title()).isEqualTo(internalTitle);
        assertThat(forAgent.get(0).score()).isGreaterThan(0.5);
    }

    @Test
    void anInternalDocumentIsNotEvidenceForACustomerEvenWhenTheQuestionQuotesItExactly() {
        String secretWord = rare();
        store("Internal exact " + secretWord, Visibility.AGENT_ONLY,
                "Goodwill credit procedure " + secretWord + " for agents only.");

        List<RetrievedChunk> evidence = retrieval.evidence("Goodwill credit procedure " + secretWord, false);

        assertThat(evidence).extracting(RetrievedChunk::text).noneMatch(text -> text.contains(secretWord));
    }

    @Test
    void resultsAreOrderedBestFirstAndLimitedToTopK() {
        String word = rare();
        store("Strong " + word, Visibility.PUBLIC, "Match " + word + " " + word + " " + word);
        store("Weaker " + word, Visibility.PUBLIC, "Match " + word + " with plenty of other words about shipping boxes");
        store("Weakest " + word, Visibility.PUBLIC, "Mention " + word + " once among many unrelated carrier tracking labels");
        store("Extra " + word, Visibility.PUBLIC, "Also " + word + " somewhere inside an even longer rambling paragraph of text");

        List<RetrievedChunk> found = retrieval.nearest("Match " + word, true);

        assertThat(found).hasSize(3);                           // top-k is 3, though four documents match
        assertThat(found.get(0).title()).isEqualTo("Strong " + word);
        assertThat(found).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
    }

    @Test
    void evidenceKeepsOnlyChunksAboveTheThreshold() {
        String word = rare();
        store("Threshold " + word, Visibility.PUBLIC, "Threshold test " + word + " " + word);

        assertThat(retrieval.evidence("Threshold test " + word, false)).isNotEmpty();
        assertThat(retrieval.evidence("completely unrelated astronomy telescope galaxy", false))
                .as("no shared words gives a score of zero, below the threshold").isEmpty();
    }

    @Test
    void aResultCarriesWhereItCameFromSoItCanBeCited() {
        String word = rare();
        IngestionResult stored = store("Source " + word, Visibility.PUBLIC,
                "# Source " + word + "\n\n## Special section\n\nCitable text " + word + " " + word);

        RetrievedChunk top = retrieval.nearest("Citable text " + word, false).get(0);

        assertThat(top.documentId()).isEqualTo(stored.documentId());
        assertThat(top.title()).isEqualTo("Source " + word);
        assertThat(top.version()).isEqualTo(1);
        assertThat(top.chunkIndex()).isZero();
        assertThat(top.section()).isNotBlank();
        assertThat(top.text()).contains(word);
    }
}
