package com.springai.knowledge;

/** Retrieved chunk with cosine similarity score. */
public record RetrievedChunk(
        long chunkId,
        long documentId,
        String title,
        int version,
        int chunkIndex,
        Integer page,
        String section,
        String text,
        double score) {
}
