package com.springai.knowledge;

/** One piece of a document: its position, the heading it sits under (if any), and its text. */
public record Chunk(int index, String section, String text) {
}
