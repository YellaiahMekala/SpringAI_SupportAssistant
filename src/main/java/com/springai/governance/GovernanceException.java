package com.springai.governance;

import org.springframework.http.HttpStatus;

import java.util.Set;

/** Pre-model rejection with a stable code and client-safe message. */
public class GovernanceException extends RuntimeException {

    public enum Code {
        SENSITIVE_DATA(HttpStatus.UNPROCESSABLE_ENTITY, "Sensitive data not accepted",
                "The request contains sensitive data (for example a password, one-time code or security code). "
                        + "Remove it and try again."),
        INPUT_TOO_LONG(HttpStatus.UNPROCESSABLE_ENTITY, "Request too long",
                "The request is too long to process. Please shorten it and try again."),
        DAILY_BUDGET_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "Daily usage limit reached",
                "The daily usage limit for this account has been reached. Please try again tomorrow.");

        private final HttpStatus status;
        private final String title;
        private final String detail;

        Code(HttpStatus status, String title, String detail) {
            this.status = status;
            this.title = title;
            this.detail = detail;
        }

        public HttpStatus status() {
            return status;
        }

        public String title() {
            return title;
        }

        public String detail() {
            return detail;
        }
    }

    private final Code code;
    private final Set<SensitiveDataPolicy.Category> categories;

    public GovernanceException(Code code, Set<SensitiveDataPolicy.Category> categories) {
        super(code.title());
        this.code = code;
        this.categories = categories == null ? Set.of() : Set.copyOf(categories);
    }

    public Code getCode() {
        return code;
    }

    /** Which kinds of sensitive data were found (never the values). Empty for other codes. */
    public Set<SensitiveDataPolicy.Category> getCategories() {
        return categories;
    }
}
