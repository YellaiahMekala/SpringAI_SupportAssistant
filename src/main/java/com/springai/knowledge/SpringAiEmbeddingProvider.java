package com.springai.knowledge;

import com.springai.governance.ModelInfo;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Spring AI {@link EmbeddingModel} adapter; validates vector dimension against the embedding table. */
@Component
class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private static final int BATCH_SIZE = 32;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingTarget target;

    SpringAiEmbeddingProvider(EmbeddingModel embeddingModel, ModelInfo modelInfo) {
        this.embeddingModel = embeddingModel;
        this.target = EmbeddingTarget.of(modelInfo.embeddingProvider());
    }

    @Override
    public EmbeddingTarget target() {
        return target;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + BATCH_SIZE));
            List<float[]> result = embeddingModel.embed(batch);
            if (result.size() != batch.size()) {
                throw new IllegalStateException("The embedding model returned " + result.size()
                        + " vectors for " + batch.size() + " texts");
            }
            for (float[] vector : result) {
                if (vector.length != target.dimensions()) {
                    throw new IllegalStateException("The embedding model returned " + vector.length
                            + " dimensions but the " + target.provider() + " table holds " + target.dimensions()
                            + ". Check the configured embedding model.");
                }
                vectors.add(vector);
            }
        }
        return vectors;
    }
}
