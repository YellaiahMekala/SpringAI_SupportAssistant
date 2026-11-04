package com.springai.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkerTest {

    @Test
    void aShortDocumentIsOneChunkKeepingItsHeading() {
        List<Chunk> chunks = new Chunker(1000, 100).split("# Returns\n\nYou can return items within 30 days.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).section()).isEqualTo("Returns");
        assertThat(chunks.get(0).text()).contains("# Returns").contains("30 days");
    }

    @Test
    void emptyOrBlankTextGivesNoChunks() {
        Chunker chunker = new Chunker(1000, 100);

        assertThat(chunker.split("")).isEmpty();
        assertThat(chunker.split("   \n\n  \t ")).isEmpty();
    }

    @Test
    void paragraphsArePackedUntilTheMaximumThenANewChunkStarts() {
        String paragraph = "word ".repeat(40).strip();   // 199 characters
        String text = String.join("\n\n", paragraph, paragraph, paragraph, paragraph, paragraph);

        List<Chunk> chunks = new Chunker(450, 0).split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(450));
    }

    @Test
    void noChunkEverExceedsTheMaximumEvenWithOverlap() {
        String text = ("Sentence number one is here. ".repeat(12) + "\n\n").repeat(20);

        List<Chunk> chunks = new Chunker(400, 120).split(text);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(400));
    }

    @Test
    void aNewChunkStartsWithTheTailOfThePreviousOne() {
        String first = "alpha ".repeat(60).strip();                 // about 360 characters
        String second = "beta " + "gamma ".repeat(40).strip();
        List<Chunk> chunks = new Chunker(420, 100).split(first + "\n\n" + second);

        assertThat(chunks).hasSize(2);
        String tail = chunks.get(0).text().substring(chunks.get(0).text().length() - 60);
        assertThat(chunks.get(1).text()).contains("alpha");      // text carried over from the first chunk
        assertThat(tail).contains("alpha");
    }

    @Test
    void aParagraphLongerThanTheMaximumIsCutAtWordBoundaries() {
        String longParagraph = "lorem ipsum dolor sit amet ".repeat(80).strip();   // about 2,100 characters

        List<Chunk> chunks = new Chunker(500, 0).split(longParagraph);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(4);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(500);
            assertThat(chunk.text()).doesNotStartWith(" ").doesNotEndWith(" ");
        });
        // No word is split: re-joining the chunks gives back the same words in the same order.
        String rejoined = chunks.stream().map(Chunk::text).collect(Collectors.joining(" "));
        assertThat(rejoined.split("\\s+")).containsExactly(longParagraph.split("\\s+"));
    }

    @Test
    void aSingleWordLongerThanTheMaximumIsStillCut() {
        List<Chunk> chunks = new Chunker(100, 0).split("x".repeat(350));

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(100));
        assertThat(chunks.stream().mapToInt(c -> c.text().length()).sum()).isEqualTo(350);
    }

    @Test
    void eachChunkIsLabelledWithTheNearestHeadingAboveItsFirstParagraph() {
        String filler = "text ".repeat(100).strip();   // about 500 characters
        String text = "# Title\n\n## Alpha\n\n" + filler + "\n\n## Beta\n\n" + filler;

        List<Chunk> chunks = new Chunker(600, 0).split(text);

        assertThat(chunks.get(0).section()).isEqualTo("Title");
        assertThat(chunks.stream().map(Chunk::section)).contains("Beta");
        assertThat(chunks.get(chunks.size() - 1).section()).isEqualTo("Beta");
    }

    @Test
    void indexesAreConsecutiveFromZero() {
        List<Chunk> chunks = new Chunker(300, 0).split(("para ".repeat(40) + "\n\n").repeat(6));

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void windowsAndUnixLineEndingsGiveTheSameChunks() {
        String unix = "# A\n\nfirst paragraph\n\nsecond paragraph";
        String windows = unix.replace("\n", "\r\n");

        assertThat(new Chunker(1000, 0).split(windows)).isEqualTo(new Chunker(1000, 0).split(unix));
    }

    @Test
    void theSameInputAlwaysGivesTheSameChunks() {
        String text = ("Some policy text goes here. ".repeat(30) + "\n\n").repeat(5);

        assertThat(new Chunker(500, 80).split(text)).isEqualTo(new Chunker(500, 80).split(text));
    }

    @Test
    void anOverlapNotSmallerThanTheMaximumIsRefused() {
        assertThatThrownBy(() -> new Chunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunker(100, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
