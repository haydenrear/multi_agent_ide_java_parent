package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionScopeServiceTest {

    @Mock
    private ArtifactEventListener artifactEventListener;
    @Mock
    private EventBus eventBus;

    @Test
    @DisplayName("completeExecution keeps active scope when persistence fails")
    void completeExecution_keepsScopeOnPersistenceFailure() {
        ExecutionScopeService service = new ExecutionScopeService(artifactEventListener);
        ReflectionTestUtils.setField(service, "eventBus", eventBus);
        ArtifactKey executionKey = ArtifactKey.createRoot();
        String workflowRunId = "workflow-run";

        service.startExecution(workflowRunId, executionKey);
        when(artifactEventListener.finishPersistRemove(executionKey.value()))
                .thenThrow(new IllegalStateException("persist failed"));

        try {
            service.completeExecution(workflowRunId, Artifact.ExecutionStatus.COMPLETED);
        } catch (IllegalStateException ignored) {
            // caller retry path should still have access to the scope
        }

        assertThat(service.getScope(workflowRunId)).isPresent();
    }

    @Test
    @DisplayName("completeExecution removes active scope after successful persistence")
    void completeExecution_removesScopeAfterSuccessfulPersistence() {
        ExecutionScopeService service = new ExecutionScopeService(artifactEventListener);
        ReflectionTestUtils.setField(service, "eventBus", eventBus);
        ArtifactKey executionKey = ArtifactKey.createRoot();
        String workflowRunId = "workflow-run-success";
        Artifact.ExecutionArtifact rootArtifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(executionKey)
                .workflowRunId(workflowRunId)
                .status(Artifact.ExecutionStatus.COMPLETED)
                .metadata(java.util.Map.of())
                .children(new java.util.ArrayList<>())
                .build();

        service.startExecution(workflowRunId, executionKey);
        when(artifactEventListener.finishPersistRemove(executionKey.value()))
                .thenReturn(Optional.of(rootArtifact));

        service.completeExecution(workflowRunId, Artifact.ExecutionStatus.COMPLETED);

        verify(artifactEventListener, times(1)).finishPersistRemove(executionKey.value());
        assertThat(service.getScope(workflowRunId)).isEmpty();
    }
}
