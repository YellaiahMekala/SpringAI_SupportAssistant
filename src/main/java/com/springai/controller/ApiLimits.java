package com.springai.controller;

/** Maximum lengths for request text, enforced by validation before any governance or model work. */
final class ApiLimits {

    /** Free-text message length in characters. The token budget is the stricter limit in practice. */
    static final int MAX_TEXT = 4000;

    /** Short fields such as a name, a topic or a level. */
    static final int MAX_SHORT = 200;

    private ApiLimits() {
    }
}
