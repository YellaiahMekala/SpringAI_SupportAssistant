package com.springai.support;

import com.springai.knowledge.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidatorTest {

    private final CitationValidator validator = new CitationValidator();

    private static RetrievedChunk chunk(long id, String title, int version, String section, String text, double score) {
        return new RetrievedChunk(id, id * 10, title, version, 0, null, section, text, score);
    }

    private final List<RetrievedChunk> retrieved = List.of(
            chunk(1, "Returns and refunds", 1, "Return window", "You can return most items within 30 days.", 0.81),
            chunk(2, "Delivery and delays", 2, "Lost packages", "A package not found after investigation is replaced.", 0.64));

    @Test
    void aMarkerThatMatchesARetrievedExcerptBecomesACitationBuiltFromThatExcerpt() {
        CitationValidator.Result result = validator.validate("You have 30 days [S1].", retrieved);

        assertThat(result.answer()).isEqualTo("You have 30 days [S1].");
        assertThat(result.invalidMarkers()).isZero();
        assertThat(result.citations()).hasSize(1);
        Citation citation = result.citations().get(0);
        assertThat(citation.id()).isEqualTo("S1");
        assertThat(citation.title()).isEqualTo("Returns and refunds");     // from the retrieved chunk, not the model
        assertThat(citation.version()).isEqualTo(1);
        assertThat(citation.section()).isEqualTo("Return window");
        assertThat(citation.documentId()).isEqualTo(10);
        assertThat(citation.score()).isEqualTo(0.81);
        assertThat(citation.excerpt()).contains("30 days");
    }

    @Test
    void anInventedMarkerIsRemovedAndCounted() {
        CitationValidator.Result result = validator.validate("Refunds take 5 days [S1] and are free [S7].", retrieved);

        assertThat(result.answer()).doesNotContain("S7").contains("[S1]");
        assertThat(result.invalidMarkers()).isEqualTo(1);
        assertThat(result.citations()).extracting(Citation::id).containsExactly("S1");
    }

    @Test
    void markerNumbersOutsideTheRetrievedRangeAreInvalid() {
        CitationValidator.Result result = validator.validate("Wrong [S0] and [S3] and [S99].", retrieved);

        assertThat(result.invalidMarkers()).isEqualTo(3);
        assertThat(result.citations()).isEmpty();
        assertThat(result.answer()).doesNotContain("[S");
    }

    @Test
    void removingAMarkerLeavesNoDoubledSpaceOrSpaceBeforePunctuation() {
        CitationValidator.Result result = validator.validate("This is allowed [S9]. And so is this [S9] too.", retrieved);

        assertThat(result.answer()).isEqualTo("This is allowed. And so is this too.");
    }

    @Test
    void citationsAreDistinctAndInOrderOfFirstUse() {
        CitationValidator.Result result =
                validator.validate("B first [S2]. Then A [S1]. Then B again [S2].", retrieved);

        assertThat(result.citations()).extracting(Citation::id).containsExactly("S2", "S1");
    }

    @Test
    void anAnswerWithoutMarkersIsReturnedExactlyAsWrittenWithNoCitations() {
        String answer = "  Spaces   and\nline breaks stay.  ";

        CitationValidator.Result result = validator.validate(answer, retrieved);

        assertThat(result.answer()).isEqualTo(answer);
        assertThat(result.citations()).isEmpty();
        assertThat(result.invalidMarkers()).isZero();
    }

    @Test
    void textThatLooksLikeAMarkerButIsNotOneIsLeftAlone() {
        CitationValidator.Result result = validator.validate("See [S] or [SX1] or [s1] or (S1) or [S 1].", retrieved);

        assertThat(result.invalidMarkers()).isZero();
        assertThat(result.answer()).contains("[S]", "[SX1]", "[s1]", "(S1)", "[S 1]");
    }

    @Test
    void withNothingRetrievedEveryMarkerIsInvalid() {
        CitationValidator.Result result = validator.validate("Made up [S1].", List.of());

        assertThat(result.citations()).isEmpty();
        assertThat(result.invalidMarkers()).isEqualTo(1);
    }

    @Test
    void emptyOrNullAnswersAreHandled() {
        assertThat(validator.validate("", retrieved).answer()).isEmpty();
        assertThat(validator.validate(null, retrieved).citations()).isEmpty();
    }

    @Test
    void aLongExcerptIsShortenedForTheResponse() {
        RetrievedChunk longChunk = chunk(3, "Long", 1, null, "word ".repeat(200), 0.9);

        Citation citation = validator.validate("x [S1]", List.of(longChunk)).citations().get(0);

        assertThat(citation.excerpt().length()).isLessThanOrEqualTo(245);
        assertThat(citation.excerpt()).endsWith("...");
        assertThat(citation.section()).isNull();
    }
}
