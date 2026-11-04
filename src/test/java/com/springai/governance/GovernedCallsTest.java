package com.springai.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The admit/settle contract of GovernedCalls, with the governance service mocked and the stream real. */
class GovernedCallsTest {

    private final GovernanceService governance = mock(GovernanceService.class);
    private final GovernedCalls governed = new GovernedCalls(governance, new ObjectMapper());
    private final Authentication auth = new TestingAuthenticationToken("user-1", "n/a");
    private final Admission admission = new Admission("req-1", 7L, LocalDate.of(2026, 1, 1), List.of("clean"), 12, 512);

    private void admitOk() {
        when(governance.admit(any(), anyString(), any())).thenReturn(admission);
        when(governance.complete(any(), anyString(), any(), any()))
                .thenReturn(new GovernanceService.UsageSummary("req-1", 12, 3, true, "LOCAL_MODEL"));
    }

    @Test
    void runPassesTheSanitizedInputsAndSettlesUsage() {
        admitOk();
        ModelUsage usage = new ModelUsage(10, 5, true);

        String result = governed.run(auth, "op", List.of("raw input"),
                inputs -> new ModelAnswer<>("answer for " + inputs.get(0), usage, "answer"));

        assertThat(result).isEqualTo("answer for clean");   // the sanitized input, not the raw one
        verify(governance).complete(admission, "op", usage, "answer");
        verify(governance, never()).abandon(any(), anyString());
    }

    @Test
    void aRejectedRequestNeverCallsTheModel() {
        when(governance.admit(any(), anyString(), any()))
                .thenThrow(new GovernanceException(GovernanceException.Code.SENSITIVE_DATA, java.util.Set.of()));
        AtomicInteger modelCalls = new AtomicInteger();

        assertThatThrownBy(() -> governed.run(auth, "op", List.of("x"), inputs -> {
            modelCalls.incrementAndGet();
            return new ModelAnswer<>("never", ModelUsage.notReported(), "");
        })).isInstanceOf(GovernanceException.class);

        assertThat(modelCalls).hasValue(0);
        verify(governance, never()).complete(any(), anyString(), any(), any());
    }

    @Test
    void aFailingModelCallReleasesTheReservation() {
        when(governance.admit(any(), anyString(), any())).thenReturn(admission);

        assertThatThrownBy(() -> governed.run(auth, "op", List.of("x"), inputs -> {
            throw new IllegalStateException("model down");
        })).isInstanceOf(IllegalStateException.class);

        verify(governance).abandon(admission, "op");
        verify(governance, never()).complete(any(), anyString(), any(), any());
    }

    @Test
    void aStreamRejectedAtAdmissionNeverCreatesAFlux() {
        when(governance.admit(any(), anyString(), any()))
                .thenThrow(new GovernanceException(GovernanceException.Code.DAILY_BUDGET_EXHAUSTED, java.util.Set.of()));
        AtomicInteger modelCalls = new AtomicInteger();

        // The exception is thrown by the call itself, before any Flux exists: an ordinary HTTP error, not a broken stream.
        assertThatThrownBy(() -> governed.stream(auth, "op", List.of("x"), inputs -> {
            modelCalls.incrementAndGet();
            return Flux.just("never");
        })).isInstanceOf(GovernanceException.class);

        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void aCompletedStreamSettlesOnceWithTheTextThatWasStreamed() {
        admitOk();

        StepVerifier.create(governed.stream(auth, "op", List.of("x"), inputs -> Flux.just("Hel", "lo ", "there")))
                .expectNext("Hel", "lo ", "there")
                .verifyComplete();

        verify(governance).complete(eq(admission), eq("op"), any(ModelUsage.class), eq("Hello there"));
    }

    @Test
    void aCancelledStreamStillSettlesTheTokensAlreadyGenerated() {
        admitOk();

        StepVerifier.create(governed.stream(auth, "op", List.of("x"), inputs -> Flux.just("a", "b", "c")), 1)
                .expectNext("a")
                .thenCancel()
                .verify();

        verify(governance).complete(eq(admission), eq("op"), any(ModelUsage.class), eq("a"));
    }

    @Test
    void aStreamThatFailsReleasesTheReservation() {
        admitOk();

        StepVerifier.create(governed.stream(auth, "op", List.of("x"),
                        inputs -> Flux.concat(Flux.just("a"), Flux.error(new IllegalStateException("broke")))))
                .expectNext("a")
                .expectError(IllegalStateException.class)
                .verify();

        verify(governance).abandon(admission, "op");
        verify(governance, never()).complete(any(), anyString(), any(), any());
    }

    @Test
    void streamEventsEmitTokenEventsThenADoneEventWithUsage() {
        admitOk();

        StepVerifier.create(governed.streamEvents(auth, "op", List.of("x"), inputs -> Flux.just("Hi", "!")))
                .assertNext(event -> assertToken(event, "Hi"))
                .assertNext(event -> assertToken(event, "!"))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("done");
                    assertThat(event.data()).contains("\"requestId\":\"req-1\"")
                            .contains("\"inputTokens\":12").contains("\"outputTokens\":3")
                            .contains("\"estimated\":true").contains("\"costStatus\":\"LOCAL_MODEL\"");
                })
                .verifyComplete();
    }

    /** Token events carry the text as a JSON string, so spaces and line breaks survive exactly. */
    private static void assertToken(ServerSentEvent<String> event, String expected) {
        assertThat(event.event()).isEqualTo("token");
        assertThat(event.data()).isEqualTo("\"" + expected + "\"");
    }

    @Test
    void tokenTextWithSpacesAndLineBreaksIsEncodedExactly() {
        admitOk();

        StepVerifier.create(governed.streamEvents(auth, "op", List.of("x"), inputs -> Flux.just(" world", "a\nb \"q\"")))
                .assertNext(event -> assertThat(event.data()).isEqualTo("\" world\""))
                .assertNext(event -> assertThat(event.data()).isEqualTo("\"a\\nb \\\"q\\\"\""))
                .assertNext(event -> assertThat(event.event()).isEqualTo("done"))
                .verifyComplete();
    }
}
