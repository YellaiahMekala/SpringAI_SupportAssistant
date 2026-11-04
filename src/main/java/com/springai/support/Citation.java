package com.springai.support;

import com.springai.knowledge.RetrievedChunk;

/** Citation built from a retrieved chunk (not from model-authored metadata). */
public record Citation(
        String id,
        long documentId,
        String title,
        int version,
        String section,
        Integer page,
        int chunkIndex,
        double score,
        String excerpt) {

    private static final int EXCERPT_LENGTH = 240;

    static Citation of(String id, RetrievedChunk chunk) {
        String text = chunk.text().replaceAll("\\s+", " ").strip();
        String excerpt = text.length() <= EXCERPT_LENGTH ? text : text.substring(0, EXCERPT_LENGTH).strip() + "...";
        return new Citation(id, chunk.documentId(), chunk.title(), chunk.version(), chunk.section(), chunk.page(),
                chunk.chunkIndex(), chunk.score(), excerpt);
    }
}
