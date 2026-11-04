package com.springai.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Support question body (optional order reference). */
public record AskRequest(
        @NotBlank @Size(max = 4000) String question,
        @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "must contain only letters, digits and hyphens")
        String orderRef) {

    /** The order reference, or null when none was given. */
    public String orderRefOrNull() {
        return orderRef == null || orderRef.isBlank() ? null : orderRef;
    }
}
