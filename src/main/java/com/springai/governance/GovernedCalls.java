package com.springai.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Single entry point for governed model calls (blocking and streaming). */
@Component
public class GovernedCalls {

    private final GovernanceService governance;
    private final ObjectMapper objectMapper;
    private final ModelGuard guard;
    private final MeterRegistry metrics;

    @Autowired
    public GovernedCalls(GovernanceService governance, ObjectMapper objectMapper, ModelGuard guard,
                         MeterRegistry metrics) {
        this.governance = governance;
        this.objectMapper = objectMapper;
        this.guard = guard;
        this.metrics = metrics;
    }

    /** Test constructor with default guard/metrics. */
    GovernedCalls(GovernanceService governance, ObjectMapper objectMapper) {
        this(governance, objectMapper, new ModelGuard(ResilienceProperties.defaults(), new SimpleMeterRegistry()),
                new SimpleMeterRegistry());
    }

    private Admission admit(Authentication authentication, String operation, List<String> inputs) {
        try {
            Admission admission = governance.admit(authentication, operation, inputs);
            metrics.counter("support.requests", "operation", operation, "result", "admitted").increment();
            return admission;
        } catch (GovernanceException e) {
            metrics.counter("support.requests", "operation", operation, "result", "rejected").increment();
            metrics.counter("support.rejections", "code", e.getCode().name()).increment();
            throw e;
        }
    }

    private GovernanceService.UsageSummary settled(GovernanceService.UsageSummary usage, String operation) {
        if (usage != null) {
            metrics.counter("support.tokens", "operation", operation, "direction", "input").increment(usage.inputTokens());
            metrics.counter("support.tokens", "operation", operation, "direction", "output").increment(usage.outputTokens());
        }
        return usage;
    }

    /** A value together with the usage summary for the call that produced it. */
    public record Result<T>(T value, GovernanceService.UsageSummary usage) {
    }

    /** Admit, call, settle. {@code call} receives the sanitized inputs and must use those, not the originals. */
    public <T> T run(Authentication authentication, String operation, List<String> inputs,
                     Function<List<String>, ModelAnswer<T>> call) {
        return runWithUsage(authentication, operation, inputs, call).value();
    }

    public <T> Result<T> runWithUsage(Authentication authentication, String operation, List<String> inputs,
                                      Function<List<String>, ModelAnswer<T>> call) {
        Admission admission = admit(authentication, operation, inputs);
        ModelAnswer<T> answer;
        try {
            answer = guard.call(() -> call.apply(admission.inputs()));
        } catch (RuntimeException e) {
            governance.abandon(admission, operation);
            throw e;
        }
        GovernanceService.UsageSummary usage = settled(
                governance.complete(admission, operation, answer.usage(), answer.outputText()), operation);
        return new Result<>(answer.value(), usage);
    }

    /** Streams plain text chunks. Admission runs now; settlement runs when the stream ends. */
    public Flux<String> stream(Authentication authentication, String operation, List<String> inputs,
                               Function<List<String>, Flux<String>> call) {
        return governedTokens(authentication, operation, inputs, call, settled -> { });
    }

    /**
     * Streams server-sent events: one {@code token} event per chunk and a final {@code done} event carrying a
     * JSON usage summary (token counts are local estimates, flagged as such).
     */
    public Flux<ServerSentEvent<String>> streamEvents(Authentication authentication, String operation,
                                                      List<String> inputs,
                                                      Function<List<String>, Flux<String>> call) {
        return streamEvents(authentication, operation, inputs, call, (usage, text) -> usage);
    }

    /**
     * Like {@link #streamEvents(Authentication, String, List, Function)}, but the final {@code done} event carries
     * whatever {@code donePayload} builds from the settled usage and the full streamed text. This is how the
     * support endpoint sends the answer's status, the checked answer and its citations, which are only known once
     * the stream has ended.
     */
    public Flux<ServerSentEvent<String>> streamEvents(Authentication authentication, String operation,
                                                      List<String> inputs,
                                                      Function<List<String>, Flux<String>> call,
                                                      BiFunction<GovernanceService.UsageSummary, String, Object> donePayload) {
        AtomicReference<Settled> settled = new AtomicReference<>();
        Flux<String> tokens = governedTokens(authentication, operation, inputs, call, settled::set);
        return tokens
                .map(token -> ServerSentEvent.<String>builder(jsonString(token)).event("token").build())
                .concatWith(Mono.fromSupplier(() -> doneEvent(settled.get(), donePayload)));
    }

    /** The usage and the full text of a stream that has ended. */
    private record Settled(GovernanceService.UsageSummary usage, String text) {
    }

    /**
     * Token text is sent as a JSON string. A plain {@code data:} value loses one leading space on the
     * receiving side (a rule of the SSE format), which would glue words together; JSON keeps every character
     * exactly, including spaces and line breaks.
     */
    private String jsonString(String text) {
        try {
            return objectMapper.writeValueAsString(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode a token as JSON", e);
        }
    }

    private Flux<String> governedTokens(Authentication authentication, String operation, List<String> inputs,
                                        Function<List<String>, Flux<String>> call,
                                        Consumer<Settled> onSettled) {
        Admission admission = admit(authentication, operation, inputs);
        Flux<String> source;
        try {
            source = guard.stream(() -> call.apply(admission.inputs()));
        } catch (RuntimeException e) {
            governance.abandon(admission, operation);
            throw e;
        }

        StringBuilder output = new StringBuilder();
        AtomicBoolean settled = new AtomicBoolean();
        Runnable settle = () -> {
            if (settled.compareAndSet(false, true)) {
                String text;
                synchronized (output) {
                    text = output.toString();
                }
                onSettled.accept(new Settled(
                        settled(governance.complete(admission, operation, ModelUsage.notReported(), text), operation),
                        text));
            }
        };
        return source
                .doOnNext(chunk -> {
                    synchronized (output) {
                        output.append(chunk);
                    }
                })
                .doOnComplete(settle)
                .doOnCancel(settle)   // the model already spent tokens up to this point
                .doOnError(error -> {
                    if (settled.compareAndSet(false, true)) {
                        governance.abandon(admission, operation);
                    }
                });
    }

    private ServerSentEvent<String> doneEvent(Settled settled,
                                              BiFunction<GovernanceService.UsageSummary, String, Object> payload) {
        try {
            String json = settled == null ? "{}" : objectMapper.writeValueAsString(
                    payload.apply(settled.usage(), settled.text()));
            return ServerSentEvent.<String>builder(json).event("done").build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder("{}").event("done").build();
        }
    }
}
