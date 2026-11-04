package com.springai.knowledge;

import org.springframework.stereotype.Service;

import java.util.List;

/** Vector retrieval with visibility filter and min-score threshold. */
@Service
public class RetrievalService {

    private final KnowledgeStore store;
    private final EmbeddingProvider embeddings;
    private final KnowledgeProperties properties;

    public RetrievalService(KnowledgeStore store, EmbeddingProvider embeddings, KnowledgeProperties properties) {
        this.store = store;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    /** The top-k closest chunks the caller may read, best first, whatever their score. */
    public List<RetrievedChunk> nearest(String question, boolean callerIsAgent) {
        float[] query = embeddings.embed(question);
        return store.search(embeddings.target(), query, callerIsAgent, properties.topK());
    }

    /** The nearest chunks that are similar enough to count as evidence. */
    public List<RetrievedChunk> evidence(String question, boolean callerIsAgent) {
        return nearest(question, callerIsAgent).stream()
                .filter(chunk -> chunk.score() >= properties.minScore())
                .toList();
    }
}
