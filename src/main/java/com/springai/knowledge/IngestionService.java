package com.springai.knowledge;

import com.springai.governance.GovernanceException;
import com.springai.governance.SensitiveDataPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Ingests a document: validate, chunk, embed, persist (shared by upload API and ingest CLI). */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int MAX_TITLE_LENGTH = 200;

    private final KnowledgeStore store;
    private final Chunker chunker;
    private final EmbeddingProvider embeddings;
    private final SensitiveDataPolicy policy;
    private final KnowledgeProperties properties;

    public IngestionService(KnowledgeStore store, Chunker chunker, EmbeddingProvider embeddings,
                            SensitiveDataPolicy policy, KnowledgeProperties properties) {
        this.store = store;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.policy = policy;
        this.properties = properties;
    }

    @Transactional
    public IngestionResult ingest(String title, Visibility visibility, byte[] content) {
        String cleanTitle = validateTitle(title);
        String text = decode(content);
        String hash = sha256(content);

        Optional<Long> existing = store.findDocumentIdByHash(hash);
        if (existing.isPresent()) {
            return embedMissing(existing.get());
        }

        List<Chunk> chunks = sanitise(chunker.split(text));
        if (chunks.isEmpty()) {
            throw new InvalidDocumentException("The document has no text to index.");
        }

        long documentId = store.insertDocument(cleanTitle, hash, visibility);
        List<Long> chunkIds = new ArrayList<>();
        for (Chunk chunk : chunks) {
            chunkIds.add(store.insertChunk(documentId, chunk));
        }
        List<float[]> vectors = embeddings.embed(chunks.stream().map(Chunk::text).toList());
        for (int i = 0; i < chunkIds.size(); i++) {
            store.insertEmbedding(embeddings.target(), chunkIds.get(i), vectors.get(i));
        }
        log.info("ingested document id={} version={} chunks={} provider={}", documentId,
                store.versionOf(documentId), chunks.size(), embeddings.target().provider());
        return new IngestionResult(documentId, cleanTitle, store.versionOf(documentId), chunks.size(),
                IngestionResult.Status.CREATED);
    }

    /** The content is already stored. Embed whatever the active provider is missing, if anything. */
    private IngestionResult embedMissing(long documentId) {
        EmbeddingTarget target = embeddings.target();
        List<KnowledgeStore.StoredChunk> missing = store.chunksWithoutEmbedding(documentId, target);
        IngestionResult.Status status = IngestionResult.Status.DUPLICATE;
        if (!missing.isEmpty()) {
            List<float[]> vectors = embeddings.embed(missing.stream().map(KnowledgeStore.StoredChunk::text).toList());
            for (int i = 0; i < missing.size(); i++) {
                store.insertEmbedding(target, missing.get(i).id(), vectors.get(i));
            }
            status = IngestionResult.Status.REINDEXED;
            log.info("re-embedded document id={} chunks={} provider={}", documentId, missing.size(), target.provider());
        }
        return new IngestionResult(documentId, store.titleOf(documentId), store.versionOf(documentId),
                store.chunkCount(documentId), status);
    }

    /** Applies the sensitive-data policy to each chunk: refuse the whole document, or use the redacted text. */
    private List<Chunk> sanitise(List<Chunk> chunks) {
        List<Chunk> result = new ArrayList<>();
        for (Chunk chunk : chunks) {
            SensitiveDataPolicy.Decision decision = policy.evaluate(chunk.text());
            if (decision.action() == SensitiveDataPolicy.Action.REJECT) {
                log.warn("document refused: sensitive data categories={}", decision.categories());
                throw new GovernanceException(GovernanceException.Code.SENSITIVE_DATA, decision.categories());
            }
            result.add(decision.action() == SensitiveDataPolicy.Action.REDACT
                    ? new Chunk(chunk.index(), chunk.section(), decision.text())
                    : chunk);
        }
        return result;
    }

    private String decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidDocumentException("The document is empty.");
        }
        if (content.length > properties.maxUploadBytes()) {
            throw new InvalidDocumentException("The document is larger than " + properties.maxUploadBytes() + " bytes.");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidDocumentException("The document is not valid UTF-8 text.");
        }
        if (text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new InvalidDocumentException("The document contains control characters, so it is not plain text.");
        }
        return text;
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidDocumentException("A title is required.");
        }
        String stripped = title.strip();
        if (stripped.length() > MAX_TITLE_LENGTH) {
            throw new InvalidDocumentException("The title is longer than " + MAX_TITLE_LENGTH + " characters.");
        }
        if (stripped.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidDocumentException("The title contains control characters.");
        }
        return stripped;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
