package com.springai.governance;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The circuit breaker around the model: opens on failures, fails fast, recovers, and records metrics. */
class ModelGuardTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private ModelGuard guard(Duration openFor) {
        // Opens after 2 failed calls out of the last 2.
        return new ModelGuard(new ResilienceProperties(50, 2, 2, openFor, 1), registry);
    }

    private static void fail(ModelGuard guard) {
        assertThatThrownBy(() -> guard.call(() -> {
            throw new IllegalStateException("model down");
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSuccessfulCallPassesThroughAndIsTimed() {
        ModelGuard guard = guard(Duration.ofHours(1));

        assertThat(guard.call(() -> "ok")).isEqualTo("ok");

        assertThat(guard.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(registry.get("support.model.calls").tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void repeatedFailuresOpenTheCircuitAndLaterCallsAreNotTried() {
        ModelGuard guard = guard(Duration.ofHours(1));
        fail(guard);
        fail(guard);
        assertThat(guard.state()).isEqualTo(CircuitBreaker.State.OPEN);

        AtomicInteger tried = new AtomicInteger();
        assertThatThrownBy(() -> guard.call(() -> {
            tried.incrementAndGet();
            return "never";
        })).isInstanceOf(ModelUnavailableException.class);

        assertThat(tried).hasValue(0);
        assertThat(registry.get("support.model.calls").tag("outcome", "rejected").counter().count()).isEqualTo(1);
        assertThat(registry.get("support.model.calls").tag("outcome", "error").timer().count()).isEqualTo(2);
        assertThat(registry.get("support.model.circuit.state").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void afterThePauseATrialCallThatSucceedsClosesTheCircuit() throws InterruptedException {
        ModelGuard guard = guard(Duration.ofMillis(100));
        fail(guard);
        fail(guard);
        assertThat(guard.state()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(250);

        assertThat(guard.call(() -> "recovered")).isEqualTo("recovered");
        assertThat(guard.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aFailingStreamCountsAsAFailureAndASuccessfulOneAsASuccess() {
        ModelGuard guard = guard(Duration.ofHours(1));

        StepVerifier.create(guard.stream(() -> Flux.just("a", "b"))).expectNext("a", "b").verifyComplete();
        StepVerifier.create(guard.stream(() -> Flux.error(new IllegalStateException("down"))))
                .expectError(IllegalStateException.class).verify();

        assertThat(registry.get("support.model.calls").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("support.model.calls").tag("outcome", "error").timer().count()).isEqualTo(1);
    }

    @Test
    void aCancelledStreamIsNotCountedAgainstTheModel() {
        ModelGuard guard = guard(Duration.ofHours(1));

        StepVerifier.create(guard.stream(() -> Flux.<String>never())).thenCancel().verify();

        assertThat(registry.find("support.model.calls").tag("outcome", "error").timer()).isNull();
        assertThat(guard.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void anOpenCircuitRejectsAStreamBeforeItStarts() {
        ModelGuard guard = guard(Duration.ofHours(1));
        fail(guard);
        fail(guard);

        assertThatThrownBy(() -> guard.stream(() -> Flux.just("x"))).isInstanceOf(ModelUnavailableException.class);
    }
}
