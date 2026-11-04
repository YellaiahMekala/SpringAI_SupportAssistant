package com.springai.governance;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.stereotype.Component;

/** Local token estimate (JTokkit / cl100k_base). */
@Component
public class TokenEstimator {

    private final JTokkitTokenCountEstimator estimator = new JTokkitTokenCountEstimator();

    public int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : estimator.estimate(text);
    }
}
