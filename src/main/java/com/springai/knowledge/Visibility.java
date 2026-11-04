package com.springai.knowledge;

/** Who may be answered from a document. */
public enum Visibility {
    /** Support policies: anyone signed in may be answered from them. */
    PUBLIC,
    /** Internal procedures: only support agents may be answered from them. */
    AGENT_ONLY
}
