package com.springai.governance;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Resilience4j circuit breaker around model calls; open circuit → {@link ModelUnavailableException}. */
@Component
public class ModelGuard {

    private final CircuitBreaker breaker;
    private final MeterRegistry registry;
    private final Duration retryAfter;

    public ModelGuard(ResilienceProperties properties, MeterRegistry registry) {
        this.registry = registry;
        this.retryAfter = properties.openFor();
        this.breaker = CircuitBreaker.of("model", CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumCalls())
                .waitDurationInOpenState(properties.openFor())
                .permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls())
                .build());
        registry.gauge("support.model.circuit.state", breaker, b -> switch (b.getState()) {
            case CLOSED -> 0;
            case OPEN, FORCED_OPEN -> 1;
            case HALF_OPEN -> 2;
            default -> 3;
        });
    }

    public CircuitBreaker.State state() {
        return breaker.getState();
    }

    /** Runs a blocking model call under the circuit breaker. */
    public <T> T call(Supplier<T> call) {
        permit();
        long start = System.nanoTime();
        try {
            T result = call.get();
            succeeded(start);
            return result;
        } catch (RuntimeException e) {
            failed(start, e);
            throw e;
        }
    }

    /** Starts a streaming model call under the circuit breaker; the outcome is recorded when the stream ends. */
    public Flux<String> stream(Supplier<Flux<String>> call) {
        permit();
        long start = System.nanoTime();
        Flux<String> source;
        try {
            source = call.get();
        } catch (RuntimeException e) {
            failed(start, e);
            throw e;
        }
        AtomicBoolean finished = new AtomicBoolean();
        return source
                .doOnComplete(() -> {
                    if (finished.compareAndSet(false, true)) {
                        succeeded(start);
                    }
                })
                .doOnError(e -> {
                    if (finished.compareAndSet(false, true)) {
                        failed(start, e);
                    }
                })
                .doOnCancel(() -> {
                    if (finished.compareAndSet(false, true)) {
                        // The client left; that says nothing about the model. Give the permission back.
                        breaker.releasePermission();
                    }
                });
    }

    private void permit() {
        if (!breaker.tryAcquirePermission()) {
            registry.counter("support.model.calls", "outcome", "rejected").increment();
            throw new ModelUnavailableException(retryAfter);
        }
    }

    private void succeeded(long start) {
        long nanos = System.nanoTime() - start;
        breaker.onSuccess(nanos, TimeUnit.NANOSECONDS);
        timer("success").record(nanos, TimeUnit.NANOSECONDS);
    }

    private void failed(long start, Throwable error) {
        long nanos = System.nanoTime() - start;
        breaker.onError(nanos, TimeUnit.NANOSECONDS, error);
        timer("error").record(nanos, TimeUnit.NANOSECONDS);
    }

    private Timer timer(String outcome) {
        return registry.timer("support.model.calls", "outcome", outcome);
    }
}
