package com.springai.governance;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Which provider and model are active, read from configuration, for usage records. */
@Component
public class ModelInfo {

    private final String provider;
    private final String embeddingProvider;
    private final String model;

    public ModelInfo(Environment environment) {
        this.provider = environment.getProperty("spring.ai.model.chat", "ollama");
        this.embeddingProvider = environment.getProperty("spring.ai.model.embedding", "ollama");
        this.model = "openai".equals(provider)
                ? environment.getProperty("spring.ai.openai.chat.options.model", "unknown")
                : environment.getProperty("spring.ai.ollama.chat.model", "unknown");
    }

    public String provider() {
        return provider;
    }

    /** The provider that creates embeddings (it follows the chat provider unless configured separately). */
    public String embeddingProvider() {
        return embeddingProvider;
    }

    public String model() {
        return model;
    }
}
