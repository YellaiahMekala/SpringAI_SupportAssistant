package com.springai.knowledge;

import java.util.List;

/** Turns text into vectors for one embedding provider. A seam so tests can use a deterministic fake. */
public interface EmbeddingProvider {

    /** The table and vector size this provider's vectors belong to. */
    EmbeddingTarget target();

    /** One vector per text, in order. Each has exactly {@code target().dimensions()} values. */
    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }
}
