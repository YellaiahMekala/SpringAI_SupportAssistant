package com.springai.knowledge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits text/Markdown into overlapping paragraph-aware chunks. */
@Component
public class Chunker {

    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    private final int maxChars;
    private final int overlapChars;

    @Autowired
    public Chunker(KnowledgeProperties properties) {
        this(properties.chunkMaxChars(), properties.chunkOverlapChars());
    }

    public Chunker(int maxChars, int overlapChars) {
        if (maxChars < 1 || overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("overlap must be smaller than the maximum chunk size");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<Chunk> split(String text) {
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalised.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentSection = null;   // section at the start of the chunk being built
        String sectionNow = null;       // nearest heading seen so far

        for (String paragraph : paragraphs(normalised)) {
            Matcher heading = HEADING.matcher(firstLine(paragraph));
            if (heading.matches() && !heading.group(1).isBlank()) {
                sectionNow = heading.group(1).strip();
            }

            for (String piece : cut(paragraph)) {
                if (current.length() > 0 && current.length() + 2 + piece.length() > maxChars) {
                    chunks.add(new Chunk(chunks.size(), currentSection, current.toString()));
                    String overlap = tail(current.toString());
                    if (overlap.length() + 2 + piece.length() > maxChars) {
                        overlap = "";   // no room for both: never exceed the maximum size
                    }
                    current = new StringBuilder(overlap);
                    currentSection = sectionNow;
                }
                if (current.length() == 0) {
                    currentSection = sectionNow;
                } else {
                    current.append("\n\n");
                }
                current.append(piece);
            }
        }
        if (current.length() > 0) {
            chunks.add(new Chunk(chunks.size(), currentSection, current.toString()));
        }
        return chunks;
    }

    private static List<String> paragraphs(String text) {
        List<String> result = new ArrayList<>();
        for (String part : BLANK_LINE.split(text)) {
            String stripped = part.strip();
            if (!stripped.isEmpty()) {
                result.add(stripped);
            }
        }
        return result;
    }

    private static String firstLine(String paragraph) {
        int newline = paragraph.indexOf('\n');
        return newline < 0 ? paragraph : paragraph.substring(0, newline);
    }

    /** Cuts an over-long paragraph at word boundaries so no piece exceeds the maximum. */
    private List<String> cut(String paragraph) {
        if (paragraph.length() <= maxChars) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        String rest = paragraph;
        while (rest.length() > maxChars) {
            int cut = rest.lastIndexOf(' ', maxChars);
            if (cut <= 0) {
                cut = maxChars;   // one very long word: cut it where it is
            }
            pieces.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            pieces.add(rest);
        }
        return pieces;
    }

    /** The last {@code overlapChars} characters of a chunk, starting on a word boundary. */
    private String tail(String chunk) {
        if (overlapChars == 0 || chunk.length() <= overlapChars) {
            return overlapChars == 0 ? "" : chunk;
        }
        String candidate = chunk.substring(chunk.length() - overlapChars);
        int space = candidate.indexOf(' ');
        return space >= 0 ? candidate.substring(space + 1) : candidate;
    }
}
