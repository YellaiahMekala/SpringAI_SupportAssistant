package com.springai.governance;

import java.time.LocalDate;
import java.util.List;

/** Result of successful admission (inputs, account, reserved tokens). */
public record Admission(
        String requestId,
        long customerId,
        LocalDate day,
        List<String> inputs,
        int estimatedInputTokens,
        long reservedTokens) {
}
