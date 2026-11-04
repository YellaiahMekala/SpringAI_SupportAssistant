package com.springai.support;

import com.springai.knowledge.RetrievalService;
import com.springai.knowledge.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/** Chooses ANSWER / CLARIFY / ABSTAIN from retrieval scores and optional order context (no model call). */
@Component
public class GroundedPlanner {

    static final String ABSTAIN_MESSAGE =
            "I could not find reliable information about that in the support documents. "
                    + "You can rephrase your question, or contact support.";
    static final String CLARIFY_MESSAGE =
            "Which order is this about? Please choose an order reference and ask again, so I can look up its details.";

    private static final Pattern ORDER_WORDS = Pattern.compile(
            "\\b(order|orders|delivery|delivered|shipment|shipping|shipped|package|parcel|tracking|arrive|arrived|late|delayed)\\b",
            Pattern.CASE_INSENSITIVE);

    /** What to do and what to send: the evidence (empty unless ANSWER) and the formatted excerpts for the prompt. */
    public record Plan(AskResponse.Status status, String cannedAnswer, List<RetrievedChunk> evidence,
                       String excerptsText) {

        public boolean callsModel() {
            return status == AskResponse.Status.ANSWER;
        }
    }

    private final RetrievalService retrieval;

    public GroundedPlanner(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    public Plan plan(String question, boolean hasOrderFacts, boolean callerIsAgent) {
        List<RetrievedChunk> evidence = retrieval.evidence(question, callerIsAgent);

        if (hasOrderFacts || !evidence.isEmpty()) {
            return new Plan(AskResponse.Status.ANSWER, null, evidence, format(evidence));
        }
        if (ORDER_WORDS.matcher(question).find()) {
            return new Plan(AskResponse.Status.CLARIFY, CLARIFY_MESSAGE, List.of(), format(List.of()));
        }
        return new Plan(AskResponse.Status.ABSTAIN, ABSTAIN_MESSAGE, List.of(), format(List.of()));
    }

    /** The excerpts as the model sees them, each with a marker it can cite. */
    static String format(List<RetrievedChunk> evidence) {
        if (evidence.isEmpty()) {
            return "No policy excerpts were found for this question.";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < evidence.size(); i++) {
            RetrievedChunk chunk = evidence.get(i);
            out.append("[S").append(i + 1).append("] ").append(chunk.title()).append(" (version ")
                    .append(chunk.version()).append(')');
            if (chunk.section() != null && !chunk.section().isBlank()) {
                out.append(", section \"").append(chunk.section()).append('"');
            }
            out.append(":\n").append(chunk.text()).append("\n\n");
        }
        return out.toString().strip();
    }
}
