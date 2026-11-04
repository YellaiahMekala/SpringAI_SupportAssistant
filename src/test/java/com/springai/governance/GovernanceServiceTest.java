package com.springai.governance;

import com.springai.budget.BudgetLedger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The settlement rules of GovernanceService: how usage, cost and the budget are recorded. */
class GovernanceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    private final BudgetLedger ledger = mock(BudgetLedger.class);
    private final CustomerAccounts accounts = mock(CustomerAccounts.class);
    private final UsageRecorder recorder = mock(UsageRecorder.class);
    private final Authentication auth = new TestingAuthenticationToken("user-1", "n/a");

    private GovernanceService service(String provider, String model, GovernanceProperties properties) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", provider)
                .withProperty("spring.ai.openai.chat.options.model", model)
                .withProperty("spring.ai.ollama.chat.model", model);
        return new GovernanceService(new SensitiveDataPolicy(), new TokenEstimator(), ledger, accounts, recorder,
                properties, new ModelInfo(env), CLOCK, 2000);
    }

    private static GovernanceProperties properties(Map<String, GovernanceProperties.ModelPrice> prices) {
        return new GovernanceProperties(500, 10, 50_000L, new GovernanceProperties.Pricing("2026-11-02", prices));
    }

    private Admission admit(GovernanceService service) {
        when(accounts.ensure(auth)).thenReturn(42L);
        when(ledger.tryConsume(eq(42L), eq(DAY), anyLong(), eq(50_000L))).thenReturn(true);
        return service.admit(auth, "op", List.of("Where is my order?"));
    }

    @Test
    void aConfiguredPriceGivesALabelledEstimatedCost() {
        // Prices are in micro-units per million tokens: 150000 means 0.15 per million input tokens.
        var prices = Map.of("gpt-4o-mini", new GovernanceProperties.ModelPrice(150_000L, 600_000L));
        GovernanceService service = service("openai", "gpt-4o-mini", properties(prices));
        Admission admission = admit(service);

        service.complete(admission, "op", new ModelUsage(2_000_000, 1_000_000, true), "text");

        ArgumentCaptor<UsageRecorder.UsageRow> row = ArgumentCaptor.forClass(UsageRecorder.UsageRow.class);
        verify(recorder).record(row.capture());
        // 2,000,000 input tokens x 150,000 / 1,000,000 = 300,000; 1,000,000 output x 600,000 / 1,000,000 = 600,000
        assertThat(row.getValue().estimatedCostMicros()).isEqualTo(900_000L);
        assertThat(row.getValue().costStatus()).isEqualTo(UsageRecorder.CostStatus.ESTIMATED);
        assertThat(row.getValue().provider()).isEqualTo("openai");
        assertThat(row.getValue().reported()).isTrue();
    }

    @Test
    void aHostedModelWithoutAPriceClaimsNoCost() {
        GovernanceService service = service("openai", "gpt-4o-mini", properties(Map.of()));
        Admission admission = admit(service);

        service.complete(admission, "op", new ModelUsage(100, 50, true), "text");

        ArgumentCaptor<UsageRecorder.UsageRow> row = ArgumentCaptor.forClass(UsageRecorder.UsageRow.class);
        verify(recorder).record(row.capture());
        assertThat(row.getValue().costStatus()).isEqualTo(UsageRecorder.CostStatus.NOT_CONFIGURED);
        assertThat(row.getValue().estimatedCostMicros()).isZero();
    }

    @Test
    void aLocalModelIsLabelledLocalWithNoCost() {
        GovernanceService service = service("ollama", "llama3.2:3b", properties(Map.of()));
        Admission admission = admit(service);

        service.complete(admission, "op", new ModelUsage(100, 50, true), "text");

        ArgumentCaptor<UsageRecorder.UsageRow> row = ArgumentCaptor.forClass(UsageRecorder.UsageRow.class);
        verify(recorder).record(row.capture());
        assertThat(row.getValue().costStatus()).isEqualTo(UsageRecorder.CostStatus.LOCAL_MODEL);
        assertThat(row.getValue().estimatedCostMicros()).isZero();
    }

    @Test
    void theBudgetIsCorrectedFromTheReservationToTheTokensActuallyUsed() {
        GovernanceService service = service("ollama", "llama3.2:3b", properties(Map.of()));
        Admission admission = admit(service);

        service.complete(admission, "op", new ModelUsage(100, 50, true), "text");

        // Actual 150 tokens minus the reservation made at admission (estimate with margin + 500 reserved output).
        verify(ledger).adjust(42L, DAY, 150 - admission.reservedTokens(), 0L);
    }

    @Test
    void whenNothingIsReportedTheOutputIsEstimatedAndRecordedAsAnEstimate() {
        GovernanceService service = service("ollama", "llama3.2:3b", properties(Map.of()));
        Admission admission = admit(service);

        GovernanceService.UsageSummary summary =
                service.complete(admission, "op", ModelUsage.notReported(), "A short answer for the customer.");

        assertThat(summary.estimated()).isTrue();
        assertThat(summary.outputTokens()).isPositive();
        assertThat(summary.inputTokens()).isEqualTo(admission.estimatedInputTokens());
    }

    @Test
    void abandoningGivesTheWholeReservationBack() {
        GovernanceService service = service("ollama", "llama3.2:3b", properties(Map.of()));
        Admission admission = admit(service);

        service.abandon(admission, "op");

        verify(ledger).adjust(42L, DAY, -admission.reservedTokens(), 0L);
        verify(recorder, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theReservationIsTheMarginedInputPlusTheReservedOutput() {
        GovernanceService service = service("ollama", "llama3.2:3b", properties(Map.of()));

        Admission admission = admit(service);

        int withMargin = (int) Math.ceil(admission.estimatedInputTokens() * 1.10);
        assertThat(admission.reservedTokens()).isEqualTo(withMargin + 500L);
    }

    @Test
    void anAccountIsNotCreatedForARequestThatIsRejected() {
        GovernanceService service = service("ollama", "llama3.2:3b", properties(Map.of()));

        assertThatThrownBy(() -> service.admit(auth, "op", List.of("my password is hunter22")))
                .isInstanceOf(GovernanceException.class);

        verify(accounts, never()).ensure(auth);
        verify(ledger, never()).tryConsume(anyLong(), org.mockito.ArgumentMatchers.any(), anyLong(), anyLong());
    }
}
