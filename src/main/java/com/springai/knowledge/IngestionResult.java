package com.springai.knowledge;

/** Result of ingest: CREATED, DUPLICATE, or REINDEXED (embeddings filled for current provider). */
public record IngestionResult(long documentId, String title, int version, int chunks, Status status) {

    public enum Status { CREATED, DUPLICATE, REINDEXED }
}
