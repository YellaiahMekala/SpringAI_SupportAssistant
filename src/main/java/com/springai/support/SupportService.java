package com.springai.support;

import io.micrometer.core.instrument.MeterRegistry;
import com.springai.governance.GovernanceService;
import com.springai.governance.GovernedCalls;
import com.springai.governance.ModelAnswer;
import com.springai.governance.ModelUsage;
import com.springai.order.CallerIdentity;
import com.springai.order.OrderResponse;
import com.springai.order.OrderService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Grounded support answers from authorised order facts and visibility-filtered document retrieval. */
@Service
public class SupportService {

    private static final String NO_ORDER_FACTS =
            "No order was specified. Do not invent order details; ask the customer for their order reference if needed.";

    private final ChatClient chatClient;
    private final OrderService orders;
    private final GovernedCalls governed;
    private final GroundedPlanner planner;
    private final CitationValidator citations;
    private final MeterRegistry metrics;

    @Value("classpath:prompts/support_system.st")
    private Resource systemPrompt;
    @Value("classpath:prompts/support_user.st")
    private Resource userPrompt;

    public SupportService(ChatClient.Builder chatClientBuilder, OrderService orders, GovernedCalls governed,
                          GroundedPlanner planner, CitationValidator citations, MeterRegistry metrics) {
        this.chatClient = chatClientBuilder.build();
        this.orders = orders;
        this.governed = governed;
        this.planner = planner;
        this.citations = citations;
        this.metrics = metrics;
    }

    /** Counts how the planner decided; the status is one of three fixed words. */
    private void countDecision(GroundedPlanner.Plan plan) {
        metrics.counter("support.decisions", "status", plan.status().name()).increment();
    }

    /** What the caller finally gets: the decision, the (checked) text and the sources. */
    private record Outcome(AskResponse.Status status, String answer, List<Citation> citations) {
    }

    public AskResponse ask(Authentication authentication, AskRequest request) {
        boolean hasOrder = request.orderRefOrNull() != null;
        String facts = orderFacts(authentication, request);
        boolean agent = CallerIdentity.from(authentication).supportAgent();
        AtomicReference<Outcome> outcome = new AtomicReference<>();

        GovernedCalls.Result<String> result = governed.runWithUsage(authentication, "support.ask",
                List.of(request.question()), inputs -> {
                    GroundedPlanner.Plan plan = planner.plan(inputs.get(0), hasOrder, agent);
                    countDecision(plan);
                    if (!plan.callsModel()) {
                        // No evidence: answer with a fixed message and do not call the model. It costs nothing.
                        outcome.set(new Outcome(plan.status(), plan.cannedAnswer(), List.of()));
                        return new ModelAnswer<>(plan.cannedAnswer(), new ModelUsage(0, 0, true), "");
                    }
                    ModelAnswer<String> answer = answer(inputs.get(0), facts, plan.excerptsText());
                    CitationValidator.Result checked = citations.validate(answer.value(), plan.evidence());
                    outcome.set(new Outcome(AskResponse.Status.ANSWER, checked.answer(), checked.citations()));
                    return new ModelAnswer<>(checked.answer(), answer.usage(), answer.outputText());
                });

        Outcome done = outcome.get();
        var usage = result.usage();
        return new AskResponse(done.status(), done.answer(), usage.requestId(),
                new AskResponse.Usage(usage.inputTokens(), usage.outputTokens(), usage.estimated(),
                        usage.costStatus()),
                done.citations());
    }

    /** What the stream's final {@code done} event carries: usage, the decision, the checked answer, the sources. */
    public record StreamDone(String requestId, int inputTokens, int outputTokens, boolean estimated,
                             String costStatus, AskResponse.Status status, String answer, List<Citation> citations) {
    }

    /**
     * Streams the answer as server-sent events: {@code token} events, then a {@code done} event with the
     * decision, the checked final answer and the citations. A stream cannot be un-sent, so the UI replaces the
     * streamed text with the checked answer when the {@code done} event arrives.
     */
    public Flux<ServerSentEvent<String>> askStream(Authentication authentication, AskRequest request) {
        boolean hasOrder = request.orderRefOrNull() != null;
        String facts = orderFacts(authentication, request);
        boolean agent = CallerIdentity.from(authentication).supportAgent();
        AtomicReference<GroundedPlanner.Plan> planned = new AtomicReference<>();

        return governed.streamEvents(authentication, "support.ask.stream", List.of(request.question()),
                inputs -> {
                    GroundedPlanner.Plan plan = planner.plan(inputs.get(0), hasOrder, agent);
                    planned.set(plan);
                    countDecision(plan);
                    if (!plan.callsModel()) {
                        return Flux.just(plan.cannedAnswer());
                    }
                    return chatClient.prompt()
                            .system(systemPrompt)
                            .user(user -> user.text(userPrompt)
                                    .param("question", inputs.get(0))
                                    .param("orderFacts", facts)
                                    .param("excerpts", plan.excerptsText()))
                            .stream()
                            .content();
                },
                (usage, text) -> donePayload(usage, text, planned.get()));
    }

    private StreamDone donePayload(GovernanceService.UsageSummary usage, String text, GroundedPlanner.Plan plan) {
        if (plan == null || !plan.callsModel()) {
            return new StreamDone(usage.requestId(), usage.inputTokens(), usage.outputTokens(), usage.estimated(),
                    usage.costStatus(), plan == null ? AskResponse.Status.ABSTAIN : plan.status(), text, List.of());
        }
        CitationValidator.Result checked = citations.validate(text, plan.evidence());
        return new StreamDone(usage.requestId(), usage.inputTokens(), usage.outputTokens(), usage.estimated(),
                usage.costStatus(), AskResponse.Status.ANSWER, checked.answer(), checked.citations());
    }

    private ModelAnswer<String> answer(String question, String facts, String excerpts) {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(user -> user.text(userPrompt)
                        .param("question", question)
                        .param("orderFacts", facts)
                        .param("excerpts", excerpts))
                .call()
                .chatResponse();
        return ModelAnswer.ofText(response);
    }

    /** Authorised order facts as plain text, or a fixed sentence when no order was given. */
    private String orderFacts(Authentication authentication, AskRequest request) {
        String orderRef = request.orderRefOrNull();
        if (orderRef == null) {
            return NO_ORDER_FACTS;
        }
        OrderResponse order = orders.getOrder(orderRef, CallerIdentity.from(authentication));
        String events = order.events().stream()
                .map(event -> event.eventType() + " at " + event.occurredAt())
                .collect(Collectors.joining("; "));
        return "Order " + order.orderRef() + ": status " + order.status()
                + "; promised date " + (order.promisedDate() == null ? "not set" : order.promisedDate())
                + "; milestones: " + (events.isEmpty() ? "none" : events) + ".";
    }
}
