package com.springai.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** CLI ingest under profile {@code ingest}: loads {@code app.knowledge.ingest-dir} then exits. */
@Component
@Profile("ingest")
class IngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final IngestionService ingestion;
    private final KnowledgeProperties properties;
    private final ApplicationContext context;

    IngestRunner(IngestionService ingestion, KnowledgeProperties properties, ApplicationContext context) {
        this.ingestion = ingestion;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path directory = Path.of(properties.ingestDir());
        int failures = 0;
        if (!Files.isDirectory(directory)) {
            log.error("ingest directory not found: {}", directory.toAbsolutePath());
            failures = 1;
        } else {
            for (Path file : documents(directory)) {
                try {
                    String name = file.getFileName().toString();
                    Visibility visibility = name.startsWith("internal-") ? Visibility.AGENT_ONLY : Visibility.PUBLIC;
                    IngestionResult result = ingestion.ingest(title(name), visibility, Files.readAllBytes(file));
                    log.info("ingest {} -> {} (document {}, version {}, {} chunks, {})", name, result.status(),
                            result.documentId(), result.version(), result.chunks(), visibility);
                } catch (RuntimeException e) {
                    failures++;
                    log.error("ingest {} FAILED: {}", file.getFileName(), e.getClass().getSimpleName());
                }
            }
        }
        int exitCode = failures == 0 ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private static List<Path> documents(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".md") || name.endsWith(".txt");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String title(String fileName) {
        String base = fileName.substring(0, fileName.lastIndexOf('.'));
        if (base.startsWith("internal-")) {
            base = base.substring("internal-".length());
        }
        return base.replace('-', ' ').replace('_', ' ').strip();
    }
}
