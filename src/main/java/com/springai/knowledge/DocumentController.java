package com.springai.knowledge;

import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Document list/upload for support agents. */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final IngestionService ingestion;
    private final KnowledgeStore store;
    private final EmbeddingProvider embeddings;

    public DocumentController(IngestionService ingestion, KnowledgeStore store, EmbeddingProvider embeddings) {
        this.ingestion = ingestion;
        this.store = store;
        this.embeddings = embeddings;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestionResult upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "title", required = false) @Size(max = 200) String title,
                                  @RequestParam(value = "visibility", defaultValue = "PUBLIC") Visibility visibility)
            throws IOException {
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!extension.equals("md") && !extension.equals("txt")) {
            throw new InvalidDocumentException("Only .md and .txt files are accepted.");
        }
        String effectiveTitle = title == null || title.isBlank() ? titleFromFileName(originalName) : title;
        return ingestion.ingest(effectiveTitle, visibility, file.getBytes());
    }

    /** One row per stored document, with how many chunks are embedded for the active provider. */
    @GetMapping
    public List<KnowledgeStore.DocumentInfo> list() {
        return store.listDocuments(embeddings.target());
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** "returns-policy.md" becomes "returns policy". Only used as a suggestion for the title. */
    private static String titleFromFileName(String fileName) {
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.replace('-', ' ').replace('_', ' ').strip();
    }
}
