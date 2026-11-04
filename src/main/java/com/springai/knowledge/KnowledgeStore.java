package com.springai.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JDBC access for documents, chunks, and provider-specific embedding tables. */
@Repository
public class KnowledgeStore {

    /** A chunk row that still needs an embedding for some provider. */
    public record StoredChunk(long id, String text) {
    }

    /** One row of the document list. */
    public record DocumentInfo(long id, String title, int version, Visibility visibility, int chunks,
                               int embeddedForProvider) {
    }

    private final JdbcTemplate jdbc;

    public KnowledgeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findDocumentIdByHash(String sha256) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM documents WHERE source_hash = ?", Long.class, sha256);
        return ids.stream().findFirst();
    }

    /** Inserts a document; its version is one more than the highest existing version with the same title. */
    public long insertDocument(String title, String sha256, Visibility visibility) {
        Long id = jdbc.queryForObject("""
                INSERT INTO documents (title, source_hash, version, visibility)
                VALUES (?, ?, COALESCE((SELECT MAX(version) FROM documents WHERE title = ?), 0) + 1, ?)
                RETURNING id
                """, Long.class, title, sha256, title, visibility.name());
        return id == null ? 0 : id;
    }

    public int versionOf(long documentId) {
        Integer version = jdbc.queryForObject("SELECT version FROM documents WHERE id = ?", Integer.class, documentId);
        return version == null ? 1 : version;
    }

    public String titleOf(long documentId) {
        return jdbc.queryForObject("SELECT title FROM documents WHERE id = ?", String.class, documentId);
    }

    public long insertChunk(long documentId, Chunk chunk) {
        Long id = jdbc.queryForObject("""
                INSERT INTO document_chunks (document_id, chunk_index, section, text)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, documentId, chunk.index(), chunk.section(), chunk.text());
        return id == null ? 0 : id;
    }

    /** Chunks of a document that have no embedding row for this provider yet. */
    public List<StoredChunk> chunksWithoutEmbedding(long documentId, EmbeddingTarget target) {
        return jdbc.query("""
                SELECT c.id, c.text FROM document_chunks c
                 WHERE c.document_id = ?
                   AND NOT EXISTS (SELECT 1 FROM %s e WHERE e.chunk_id = c.id)
                 ORDER BY c.chunk_index
                """.formatted(target.table()),
                (rs, i) -> new StoredChunk(rs.getLong("id"), rs.getString("text")), documentId);
    }

    public int chunkCount(long documentId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }

    public void insertEmbedding(EmbeddingTarget target, long chunkId, float[] vector) {
        jdbc.update("INSERT INTO %s (chunk_id, embedding) VALUES (?, ?::vector)".formatted(target.table()),
                chunkId, toVectorLiteral(vector));
    }

    /** Nearest chunks; visibility filter is applied in SQL. */
    public List<RetrievedChunk> search(EmbeddingTarget target, float[] query, boolean includeAgentOnly, int limit) {
        String vector = toVectorLiteral(query);
        return jdbc.query("""
                SELECT c.id AS chunk_id, d.id AS document_id, d.title, d.version, c.chunk_index, c.page,
                       c.section, c.text, 1 - (e.embedding <=> ?::vector) AS score
                  FROM %s e
                  JOIN document_chunks c ON c.id = e.chunk_id
                  JOIN documents d ON d.id = c.document_id
                 WHERE (d.visibility = 'PUBLIC' OR ?)
                 ORDER BY e.embedding <=> ?::vector
                 LIMIT ?
                """.formatted(target.table()),
                (rs, i) -> new RetrievedChunk(
                        rs.getLong("chunk_id"), rs.getLong("document_id"), rs.getString("title"),
                        rs.getInt("version"), rs.getInt("chunk_index"),
                        (Integer) rs.getObject("page"), rs.getString("section"), rs.getString("text"),
                        rs.getDouble("score")),
                vector, includeAgentOnly, vector, limit);
    }

    public List<DocumentInfo> listDocuments(EmbeddingTarget target) {
        return jdbc.query("""
                SELECT d.id, d.title, d.version, d.visibility,
                       (SELECT count(*) FROM document_chunks c WHERE c.document_id = d.id) AS chunks,
                       (SELECT count(*) FROM document_chunks c JOIN %s e ON e.chunk_id = c.id
                         WHERE c.document_id = d.id) AS embedded
                  FROM documents d ORDER BY d.title, d.version
                """.formatted(target.table()),
                (rs, i) -> new DocumentInfo(rs.getLong("id"), rs.getString("title"), rs.getInt("version"),
                        Visibility.valueOf(rs.getString("visibility")), rs.getInt("chunks"), rs.getInt("embedded")));
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
