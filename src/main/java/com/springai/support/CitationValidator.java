package com.springai.support;

import com.springai.knowledge.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps only citation markers that match retrieved chunks; builds {@link Citation} from retrieval metadata. */
@Component
public class CitationValidator {

    private static final Pattern MARKER = Pattern.compile("\\[S(\\d{1,3})]");

    /** The answer with invalid markers removed, the valid citations in order of first use, and how many were invalid. */
    public record Result(String answer, List<Citation> citations, int invalidMarkers) {
    }

    public Result validate(String answer, List<RetrievedChunk> retrieved) {
        if (answer == null || answer.isEmpty()) {
            return new Result("", List.of(), 0);
        }
        Map<String, Citation> used = new LinkedHashMap<>();
        int invalid = 0;

        Matcher matcher = MARKER.matcher(answer);
        StringBuilder cleaned = new StringBuilder();
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (number >= 1 && number <= retrieved.size()) {
                String id = "S" + number;
                used.computeIfAbsent(id, key -> Citation.of(key, retrieved.get(number - 1)));
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement(matcher.group()));
            } else {
                invalid++;
                matcher.appendReplacement(cleaned, "");
            }
        }
        matcher.appendTail(cleaned);

        // Only touch the text when a marker was actually removed; a clean answer is returned exactly as written.
        String finalAnswer = invalid > 0 ? tidy(cleaned.toString()) : answer;
        return new Result(finalAnswer, new ArrayList<>(used.values()), invalid);
    }

    /** Removing a marker can leave a doubled space or a space before punctuation; clean those up. */
    private static String tidy(String text) {
        return text.replaceAll("[ \\t]{2,}", " ").replaceAll(" +([.,;:!?])", "$1").strip();
    }
}
