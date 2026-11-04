package com.springai.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springai.TestPostgresConfig;
import com.springai.knowledge.EmbeddingProvider;
import com.springai.knowledge.IngestionService;
import com.springai.knowledge.KnowledgeProperties;
import com.springai.knowledge.KnowledgeStore;
import com.springai.knowledge.RetrievalService;
import com.springai.knowledge.Visibility;
import com.springai.support.GroundedPlanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression gate: evaluation/questions.json against planner+retrieval; report in target/evaluation-report.md. */
@SpringBootTest
@Import(TestPostgresConfig.class)
@ActiveProfiles("demo")
@Testcontainers(disabledWithoutDocker = true)
class EvaluationRunnerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private IngestionService ingestion;
    @Autowired
    private KnowledgeStore store;
    @Autowired
    private EmbeddingProvider embeddings;
    @Autowired
    private KnowledgeProperties properties;

    private record Outcome(JsonNode question, String actual, List<String> titles, boolean actionOk, Boolean docHit,
                           boolean leaked) {
    }

    private record Rates(double action, double hit, long leaks) {
    }

    @Test
    void theQuestionSetMeetsTheBaselineAndNeverLeaksInternalText() throws IOException {
        ingestCorpus();
        JsonNode set = JSON.readTree(Files.readString(Path.of("evaluation", "questions.json")));
        JsonNode baseline = JSON.readTree(Files.readString(Path.of("evaluation", "baseline.json")));

        double gateScore = baseline.get("minScore").asDouble();
        List<Outcome> outcomes = evaluate(set, gateScore);
        Rates rates = rates(outcomes);

        List<String> sweep = new ArrayList<>();
        for (double score : new double[] {0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50}) {
            Rates r = rates(evaluate(set, score));
            sweep.add(String.format("| %.2f | %.1f%% | %.1f%% | %d |", score, r.action() * 100, r.hit() * 100, r.leaks()));
        }
        writeReport(outcomes, rates, gateScore, sweep, set.get("version").asText());

        assertThat(rates.leaks()).as("internal text shown to a customer").isZero();
        assertThat(rates.action()).as("action accuracy").isGreaterThanOrEqualTo(baseline.get("actionAccuracy").asDouble());
        assertThat(rates.hit()).as("expected document retrieved").isGreaterThanOrEqualTo(baseline.get("documentHitRate").asDouble());
    }

    private Rates rates(List<Outcome> outcomes) {
        long actionOk = outcomes.stream().filter(Outcome::actionOk).count();
        long withDoc = outcomes.stream().filter(o -> o.docHit() != null).count();
        long docHits = outcomes.stream().filter(o -> Boolean.TRUE.equals(o.docHit())).count();
        long leaks = outcomes.stream().filter(Outcome::leaked).count();
        return new Rates((double) actionOk / outcomes.size(), withDoc == 0 ? 1.0 : (double) docHits / withDoc, leaks);
    }

    /** Runs every question through a planner whose retrieval uses the given minimum score. */
    private List<Outcome> evaluate(JsonNode set, double minScore) {
        KnowledgeProperties at = new KnowledgeProperties(properties.topK(), minScore, properties.chunkMaxChars(),
                properties.chunkOverlapChars(), properties.maxUploadBytes(), properties.ingestDir());
        GroundedPlanner planner = new GroundedPlanner(new RetrievalService(store, embeddings, at));
        List<Outcome> outcomes = new ArrayList<>();
        for (JsonNode q : set.get("questions")) {
            boolean agent = q.get("asAgent").asBoolean();
            GroundedPlanner.Plan plan = planner.plan(q.get("question").asText(), q.get("hasOrder").asBoolean(), agent);
            List<String> titles = plan.evidence().stream().map(c -> c.title()).distinct().toList();
            String expect = q.get("expect").asText();
            boolean actionOk = expect.equals("*") || plan.status().name().equals(expect);
            JsonNode expectDoc = q.get("expectDoc");
            Boolean docHit = expectDoc.isNull() ? null : titles.contains(expectDoc.asText());
            JsonNode forbid = q.get("forbidDoc");
            boolean leaked = !agent && !forbid.isNull() && titles.contains(forbid.asText());
            outcomes.add(new Outcome(q, plan.status().name(), titles, actionOk, docHit, leaked));
        }
        return outcomes;
    }

    private void ingestCorpus() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("knowledge"))) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".md")).toList()) {
                String text = Files.readString(file);
                String title = text.lines().filter(l -> l.startsWith("# ")).findFirst()
                        .map(l -> l.substring(2).trim()).orElse(file.getFileName().toString());
                Visibility visibility = file.getFileName().toString().startsWith("internal-")
                        ? Visibility.AGENT_ONLY : Visibility.PUBLIC;
                ingestion.ingest(title, visibility, text.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void writeReport(List<Outcome> outcomes, Rates rates, double gateScore, List<String> sweep, String version)
            throws IOException {
        Map<String, int[]> perCategory = new LinkedHashMap<>();
        for (Outcome o : outcomes) {
            int[] counts = perCategory.computeIfAbsent(o.question().get("category").asText(), k -> new int[2]);
            counts[0]++;
            if (o.actionOk() && !Boolean.FALSE.equals(o.docHit()) && !o.leaked()) {
                counts[1]++;
            }
        }
        StringBuilder md = new StringBuilder();
        md.append("# Evaluation report\n\n");
        md.append("Date: ").append(LocalDate.now()).append("  \n");
        md.append("Questions: ").append(outcomes.size()).append(" (version ").append(version).append(")  \n");
        md.append("Embeddings: deterministic bag-of-words hash (test fake); chat model: none. Gate min-score ")
                .append(gateScore).append(", top-k ").append(properties.topK()).append("\n\n");
        md.append("These numbers describe the pipeline on a synthetic question set. They are not a measure of answer ")
                .append("quality or of retrieval quality with real embedding models.\n\n");
        md.append("| Measure | Result |\n|---|---|\n");
        md.append(String.format("| Action accuracy (answer / clarify / abstain) | %.1f%% |%n", rates.action() * 100));
        md.append(String.format("| Expected document retrieved | %.1f%% |%n", rates.hit() * 100));
        md.append("| Internal text shown to a customer | ").append(rates.leaks()).append(" |\n\n");
        md.append("| Category | Questions | Fully correct |\n|---|---|---|\n");
        perCategory.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v[0]).append(" | ").append(v[1]).append(" |\n"));
        md.append("\n## Threshold sweep (same questions, different minimum score)\n\n");
        md.append("| min-score | Action accuracy | Document hit | Leaks |\n|---|---|---|---|\n");
        sweep.forEach(row -> md.append(row).append("\n"));
        md.append("\n## Failures\n\n");
        boolean any = false;
        for (Outcome o : outcomes) {
            if (!o.actionOk() || Boolean.FALSE.equals(o.docHit()) || o.leaked()) {
                any = true;
                md.append("- ").append(o.question().get("id").asText()).append(" [").append(o.question().get("category").asText())
                        .append("] \"").append(o.question().get("question").asText()).append("\": expected ")
                        .append(o.question().get("expect").asText()).append(", got ").append(o.actual())
                        .append("; retrieved ").append(o.titles()).append("\n");
            }
        }
        if (!any) {
            md.append("None.\n");
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "evaluation-report.md"), md.toString());
    }
}
