package com.springai.governance;

import com.springai.budget.BudgetLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Admit (policy, size, budget reserve) and settle/release usage for model calls. */
@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);

    private final SensitiveDataPolicy policy;
    private final TokenEstimator estimator;
    private final BudgetLedger ledger;
    private final CustomerAccounts accounts;
    private final UsageRecorder usageRecorder;
    private final GovernanceProperties properties;
    private final ModelInfo modelInfo;
    private final Clock clock;
    private final int maxInputTokens;

    public GovernanceService(SensitiveDataPolicy policy,
                             TokenEstimator estimator,
                             BudgetLedger ledger,
                             CustomerAccounts accounts,
                             UsageRecorder usageRecorder,
                             GovernanceProperties properties,
                             ModelInfo modelInfo,
                             Clock clock,
                             @Value("${app.token-budget.max-input-tokens:2000}") int maxInputTokens) {
        this.policy = policy;
        this.estimator = estimator;
        this.ledger = ledger;
        this.accounts = accounts;
        this.usageRecorder = usageRecorder;
        this.properties = properties;
        this.modelInfo = modelInfo;
        this.clock = clock;
        this.maxInputTokens = maxInputTokens;
    }

    /** Checks the inputs and reserves budget. Throws {@link GovernanceException} when the request must stop. */
    public Admission admit(Authentication authentication, String operation, List<String> inputs) {
        String requestId = currentRequestId();

        List<String> sanitized = new ArrayList<>();
        Set<SensitiveDataPolicy.Category> redacted = EnumSet.noneOf(SensitiveDataPolicy.Category.class);
        for (String input : inputs) {
            SensitiveDataPolicy.Decision decision = policy.evaluate(input);
            if (decision.action() == SensitiveDataPolicy.Action.REJECT) {
                log.warn("governance rejected request={} operation={} reason=SENSITIVE_DATA categories={}",
                        requestId, operation, decision.categories());
                throw new GovernanceException(GovernanceException.Code.SENSITIVE_DATA, decision.categories());
            }
            if (decision.action() == SensitiveDataPolicy.Action.REDACT) {
                redacted.addAll(decision.categories());
            }
            sanitized.add(decision.text());
        }

        int estimatedInput = sanitized.stream().mapToInt(estimator::estimate).sum();
        int withMargin = (int) Math.ceil(estimatedInput * (100 + properties.safetyMarginPercent()) / 100.0);
        if (withMargin > maxInputTokens) {
            log.warn("governance rejected request={} operation={} reason=INPUT_TOO_LONG estimatedTokens={} limit={}",
                    requestId, operation, withMargin, maxInputTokens);
            throw new GovernanceException(GovernanceException.Code.INPUT_TOO_LONG, Set.of());
        }

        long customerId = accounts.ensure(authentication);
        LocalDate day = LocalDate.now(clock);
        long reserved = (long) withMargin + properties.reservedOutputTokens();
        if (!ledger.tryConsume(customerId, day, reserved, properties.dailyTokenLimit())) {
            log.warn("governance rejected request={} operation={} reason=DAILY_BUDGET_EXHAUSTED", requestId, operation);
            throw new GovernanceException(GovernanceException.Code.DAILY_BUDGET_EXHAUSTED, Set.of());
        }

        log.info("governance admitted request={} operation={} estimatedInputTokens={} reservedTokens={} redacted={}",
                requestId, operation, estimatedInput, reserved, redacted);
        return new Admission(requestId, customerId, day, List.copyOf(sanitized), estimatedInput, reserved);
    }

    /** Settles an admitted call: records usage and corrects the reservation to the tokens actually used. */
    public UsageSummary complete(Admission admission, String operation, ModelUsage usage, String outputText) {
        int inputTokens;
        int outputTokens;
        boolean reported = usage != null && usage.reported();
        if (reported) {
            inputTokens = usage.inputTokens();
            outputTokens = usage.outputTokens();
        } else {
            inputTokens = admission.estimatedInputTokens();
            outputTokens = estimator.estimate(outputText);
        }

        long cost = 0;
        UsageRecorder.CostStatus costStatus;
        if (!"openai".equals(modelInfo.provider())) {
            costStatus = UsageRecorder.CostStatus.LOCAL_MODEL;
        } else {
            GovernanceProperties.ModelPrice price = properties.pricing().models().get(modelInfo.model());
            if (price == null) {
                costStatus = UsageRecorder.CostStatus.NOT_CONFIGURED;
            } else {
                cost = (inputTokens * price.inputPerMillionMicros() + outputTokens * price.outputPerMillionMicros())
                        / 1_000_000L;
                costStatus = UsageRecorder.CostStatus.ESTIMATED;
            }
        }

        long actual = (long) inputTokens + outputTokens;
        ledger.adjust(admission.customerId(), admission.day(), actual - admission.reservedTokens(), cost);
        usageRecorder.record(new UsageRecorder.UsageRow(admission.customerId(), admission.requestId(),
                modelInfo.provider(), modelInfo.model(), operation, admission.estimatedInputTokens(),
                inputTokens, outputTokens, reported, cost, costStatus));

        log.info("governance settled request={} operation={} inputTokens={} outputTokens={} reported={} costStatus={}",
                admission.requestId(), operation, inputTokens, outputTokens, reported, costStatus);
        return new UsageSummary(admission.requestId(), inputTokens, outputTokens, !reported, costStatus.name());
    }

    /** Gives the reservation back when the call failed before producing an answer. */
    public void abandon(Admission admission, String operation) {
        ledger.adjust(admission.customerId(), admission.day(), -admission.reservedTokens(), 0);
        log.info("governance released reservation request={} operation={}", admission.requestId(), operation);
    }

    private static String currentRequestId() {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    /** What the caller may be told about a call's usage. {@code estimated} is true when counts are local estimates. */
    public record UsageSummary(String requestId, int inputTokens, int outputTokens, boolean estimated,
                               String costStatus) {
    }
}
