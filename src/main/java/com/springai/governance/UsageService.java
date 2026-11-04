package com.springai.governance;

import com.springai.budget.BudgetLedger;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** Reports the signed-in user's token use for the current UTC day. */
@Service
public class UsageService {

    public record UsageToday(LocalDate day, long tokensUsed, long dailyLimit, long remainingTokens, String note) {
    }

    private final CustomerAccounts accounts;
    private final BudgetLedger ledger;
    private final GovernanceProperties properties;
    private final Clock clock;

    public UsageService(CustomerAccounts accounts, BudgetLedger ledger, GovernanceProperties properties, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.properties = properties;
        this.clock = clock;
    }

    public UsageToday today(Authentication authentication) {
        long customerId = accounts.ensure(authentication);
        LocalDate day = LocalDate.now(clock);
        long used = ledger.tokensUsed(customerId, day);
        long limit = properties.dailyTokenLimit();
        return new UsageToday(day, used, limit, Math.max(0, limit - used),
                "Token counts are estimates unless the provider reported them. The day resets at 00:00 UTC.");
    }
}
