package com.springai.knowledge;

/** Provider → embedding table and fixed vector dimension. */
public enum EmbeddingTarget {
    OLLAMA("ollama", "chunk_embeddings_ollama", 768),
    OPENAI("openai", "chunk_embeddings_openai", 1536);

    private final String provider;
    private final String table;
    private final int dimensions;

    EmbeddingTarget(String provider, String table, int dimensions) {
        this.provider = provider;
        this.table = table;
        this.dimensions = dimensions;
    }

    public String provider() {
        return provider;
    }

    public String table() {
        return table;
    }

    public int dimensions() {
        return dimensions;
    }

    public static EmbeddingTarget of(String provider) {
        for (EmbeddingTarget target : values()) {
            if (target.provider.equals(provider)) {
                return target;
            }
        }
        throw new IllegalStateException("No embedding table is defined for provider '" + provider + "'");
    }
}
