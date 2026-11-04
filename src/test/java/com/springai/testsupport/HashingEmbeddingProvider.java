package com.springai.testsupport;

import com.springai.knowledge.EmbeddingProvider;
import com.springai.knowledge.EmbeddingTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A deterministic stand-in for an embedding model, for tests. Each text becomes a vector by hashing its words into
 * 768 buckets and normalising, so two texts that share words get a high cosine similarity and texts with no words
 * in common get zero. It needs no model server and no network. It is not semantic: it only matches words (a
 * simple plural is folded), which is enough to test retrieval, thresholds and access rules.
 */
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private static final EmbeddingTarget TARGET = EmbeddingTarget.OLLAMA;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "you", "your", "can", "with", "that", "this", "what", "when", "how", "many",
            "much", "have", "has", "does", "not", "any", "from", "will", "was", "were", "its", "all", "our", "who",
            "why", "which", "there", "their", "about", "into", "than", "then", "also", "but", "may", "should",
            "would", "could", "they", "them", "been", "being", "after", "before", "within", "need", "needs");

    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean failing;

    @Override
    public EmbeddingTarget target() {
        return TARGET;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        calls.incrementAndGet();
        if (failing) {
            throw new IllegalStateException("embedding provider unavailable (test)");
        }
        List<float[]> vectors = new ArrayList<>();
        for (String text : texts) {
            vectors.add(vectorOf(text));
        }
        return vectors;
    }

    /** How many times embed was called (to prove a rejected request never reached the provider). */
    public int calls() {
        return calls.get();
    }

    public void resetCalls() {
        calls.set(0);
    }

    /** Makes every later call fail, to test that a failed embedding leaves nothing behind. */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    static float[] vectorOf(String text) {
        float[] vector = new float[TARGET.dimensions()];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() < 3 || STOP_WORDS.contains(word)) {
                continue;
            }
            String stem = word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
            vector[Math.floorMod(stem.hashCode(), vector.length)] += 1f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= scale;
            }
        }
        return vector;
    }
}
