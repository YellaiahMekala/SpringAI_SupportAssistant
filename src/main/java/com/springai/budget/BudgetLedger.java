package com.springai.budget;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Per-customer daily token budget; consume uses a single conditional UPDATE. */
@Repository
public class BudgetLedger {

    private final JdbcTemplate jdbc;

    public BudgetLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Atomically consume tokens if within the daily limit; returns false when the limit would be exceeded. */
    @Transactional
    public boolean tryConsume(long customerId, LocalDate day, long tokens, long dailyLimitTokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must not be negative");
        }
        jdbc.update("INSERT INTO budget_ledger (customer_id, usage_day) VALUES (?, ?) ON CONFLICT DO NOTHING",
                customerId, day);
        int updated = jdbc.update("""
                UPDATE budget_ledger
                   SET tokens_used = tokens_used + ?
                 WHERE customer_id = ? AND usage_day = ? AND tokens_used + ? <= ?
                """, tokens, customerId, day, tokens, dailyLimitTokens);
        return updated == 1;
    }

    /**
     * Settles a reservation against what was actually used: adds {@code deltaTokens} (negative to give tokens
     * back) and {@code costMicros} to the day's totals. Unlike {@link #tryConsume} this never refuses: the
     * tokens were already spent by the model, so the ledger must record them even if that crosses the limit.
     * The token total never goes below zero.
     */
    public void adjust(long customerId, LocalDate day, long deltaTokens, long costMicros) {
        jdbc.update("""
                UPDATE budget_ledger
                   SET tokens_used = GREATEST(0, tokens_used + ?), cost_micros = cost_micros + ?
                 WHERE customer_id = ? AND usage_day = ?
                """, deltaTokens, costMicros, customerId, day);
    }

    /** Tokens already spent by the customer on {@code day}; 0 when nothing was recorded. */
    public long tokensUsed(long customerId, LocalDate day) {
        Long used = jdbc.queryForObject(
                "SELECT COALESCE((SELECT tokens_used FROM budget_ledger WHERE customer_id = ? AND usage_day = ?), 0)",
                Long.class, customerId, day);
        return used == null ? 0 : used;
    }
}
