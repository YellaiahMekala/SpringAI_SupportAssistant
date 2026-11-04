package com.springai.support;

import com.springai.knowledge.RetrievalService;
import com.springai.knowledge.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroundedPlannerTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final GroundedPlanner planner = new GroundedPlanner(retrieval);

    private static RetrievedChunk chunk(String title, String section, String text) {
        return new RetrievedChunk(1, 1, title, 1, 0, null, section, text, 0.8);
    }

    private void evidence(RetrievedChunk... chunks) {
        when(retrieval.evidence(anyString(), anyBoolean())).thenReturn(List.of(chunks));
    }

    @Test
    void enoughEvidenceMeansAnswerWithTheFormattedExcerpts() {
        evidence(chunk("Returns and refunds", "Return window", "Return within 30 days."));

        GroundedPlanner.Plan plan = planner.plan("How long do I have to return something?", false, false);

        assertThat(plan.status()).isEqualTo(AskResponse.Status.ANSWER);
        assertThat(plan.callsModel()).isTrue();
        assertThat(plan.excerptsText()).startsWith("[S1] Returns and refunds (version 1), section \"Return window\":")
                .contains("Return within 30 days.");
        assertThat(plan.cannedAnswer()).isNull();
    }

    @Test
    void excerptsAreNumberedInOrderSoTheModelCanCiteThem() {
        evidence(chunk("A", null, "first"), chunk("B", "Sec", "second"));

        String excerpts = planner.plan("question about things", false, false).excerptsText();

        assertThat(excerpts).contains("[S1] A (version 1):").contains("[S2] B (version 1), section \"Sec\":");
        assertThat(excerpts.indexOf("[S1]")).isLessThan(excerpts.indexOf("[S2]"));
    }

    @Test
    void anOrderInPlayIsEnoughToAnswerEvenWithoutAnyDocumentEvidence() {
        evidence();

        GroundedPlanner.Plan plan = planner.plan("Why is it late?", true, false);

        assertThat(plan.status()).isEqualTo(AskResponse.Status.ANSWER);
        assertThat(plan.excerptsText()).contains("No policy excerpts were found");
    }

    @Test
    void noOrderAndNoEvidenceForAnOrderQuestionAsksWhichOrder() {
        evidence();

        for (String question : List.of("Where is my package?", "My delivery has not arrived", "is it delayed")) {
            GroundedPlanner.Plan plan = planner.plan(question, false, false);

            assertThat(plan.status()).as(question).isEqualTo(AskResponse.Status.CLARIFY);
            assertThat(plan.callsModel()).isFalse();
            assertThat(plan.cannedAnswer()).isEqualTo(GroundedPlanner.CLARIFY_MESSAGE);
        }
    }

    @Test
    void noOrderAndNoEvidenceForAnythingElseAbstainsWithoutCallingTheModel() {
        evidence();

        GroundedPlanner.Plan plan = planner.plan("What is the capital of France?", false, false);

        assertThat(plan.status()).isEqualTo(AskResponse.Status.ABSTAIN);
        assertThat(plan.callsModel()).isFalse();
        assertThat(plan.cannedAnswer()).isEqualTo(GroundedPlanner.ABSTAIN_MESSAGE);
        assertThat(plan.evidence()).isEmpty();
    }

    @Test
    void theCallersRoleIsPassedToRetrievalSoRestrictedDocumentsStayRestricted() {
        evidence();

        planner.plan("anything about refunds", false, true);
        verify(retrieval).evidence("anything about refunds", true);

        planner.plan("anything about refunds", false, false);
        verify(retrieval).evidence("anything about refunds", false);
    }
}
