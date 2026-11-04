# Evaluation

Regression checks for the grounded-answer pipeline (action choice, document hit, no internal leak to customers).

| File | Purpose |
|------|---------|
| `questions.json` | 66 synthetic questions with expected actions |
| `baseline.json` | Minimum accuracy thresholds for CI |
| `EvaluationRunnerTest` | Ingests `knowledge/`, runs the set, writes `target/evaluation-report.md` |

```bash
./mvnw -Dtest=EvaluationRunnerTest test
```

Uses a deterministic fake embedder (no live model). Results guard pipeline behaviour on this set; they are not a claim about live-model answer quality.
