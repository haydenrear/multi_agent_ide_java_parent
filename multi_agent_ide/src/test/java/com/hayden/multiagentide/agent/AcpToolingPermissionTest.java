package com.hayden.multiagentide.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.acp_cdc_ai.sandbox.SandboxContext;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.gate.PermissionGateAdapter;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ExtendWith(MockitoExtension.class)
class AcpToolingPermissionTest {

    @TempDir
    Path tempDir;

    private String sessionId;
    private AcpTooling acpTooling;
    private PermissionGate permissionGate;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        sessionId = ArtifactKey.createRoot().value();

        RequestContext context = RequestContext.builder()
                .sessionId(sessionId)
                .sandboxContext(SandboxContext.builder()
                        .mainWorktreePath(tempDir)
                        .build())
                .build();

        RequestContextRepository requestContextRepository = new RequestContextRepository() {
            @Override
            public RequestContext save(RequestContext ctx) {
                return ctx;
            }

            @Override
            public Optional<RequestContext> findBySessionId(String sid) {
                return sessionId.equals(sid) ? Optional.of(context) : Optional.empty();
            }

            @Override
            public void deleteBySessionId(String sid) {
            }

            @Override
            public void clear() {
            }
        };

        GraphRepository graphRepository = mock(GraphRepository.class);
        ComputationGraphOrchestrator orchestrator = mock(ComputationGraphOrchestrator.class);
        eventBus = mock(EventBus.class);
        permissionGate = new PermissionGate(graphRepository, orchestrator);
        ReflectionTestUtils.setField(permissionGate, "eventBus", eventBus);

        PermissionGateAdapter permissionGateAdapter = new PermissionGateAdapter(permissionGate);
        acpTooling = new AcpTooling(requestContextRepository, new ObjectMapper());
        acpTooling.setPermissionGate(permissionGateAdapter);

        EventBus.Process.set(new EventBus.AgentNodeKey(ArtifactKey.createRoot().value()));
    }

    @AfterEach
    void tearDown() {
        EventBus.Process.set(null);
    }

    @Test
    void bashRequestsPermissionAndAllowsCommands() throws Exception {
        Path touched = tempDir.resolve("allowed.txt");

        String touchOutput = runWithPermissionResolution("touch allowed.txt", true);
        assertThat(touchOutput).doesNotContain("Permission denied");
        assertThat(Files.exists(touched)).isTrue();

        String lsOutput = runWithPermissionResolution("ls", true);
        assertThat(lsOutput).contains("allowed.txt");

        String findOutput = runWithPermissionResolution("find . -maxdepth 1 -type f", true);
        assertThat(findOutput).contains("./allowed.txt");
    }

    @Test
    void bashDeniesPermission() throws Exception {
        Path denied = tempDir.resolve("denied.txt");

        String output = runWithPermissionResolution("touch denied.txt", false);

        assertThat(output).contains("Permission denied for bash command.");
        assertThat(Files.exists(denied)).isFalse();

        ArgumentCaptor<Events.GraphEvent> captor = ArgumentCaptor.forClass(Events.GraphEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        boolean cancelledEventFound = captor.getAllValues().stream()
                .filter(event -> event instanceof Events.PermissionResolvedEvent)
                .map(event -> (Events.PermissionResolvedEvent) event)
                .anyMatch(event -> event.outcome() != null && event.outcome().toLowerCase().contains("cancel"));
        assertThat(cancelledEventFound).isTrue();
    }

    @Test
    void expiredBackgroundProcessIsCleanedUp() throws Exception {
        acpTooling.setBackgroundProcessTtlMillis(5);

        String output = runWithPermissionResolution("sleep 5", true, true);
        String bashId = extractBashId(output);

        Thread.sleep(10);

        String cleanupTrigger = runWithPermissionResolution("ls", true);
        assertThat(cleanupTrigger).doesNotContain("Permission denied");

        String result = acpTooling.bashOutput(sessionId, bashId, null);
        assertThat(result).contains("No background shell found with ID");
    }

    private String runWithPermissionResolution(String command, boolean allow) {
        return runWithPermissionResolution(command, allow, false);
    }

    private String runWithPermissionResolution(String command, boolean allow, boolean runInBackground) {
        AtomicBoolean permissionRequested = new AtomicBoolean(false);

        CompletableFuture.runAsync(() -> {
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !permissionGate.pendingPermissionRequests().isEmpty());
            permissionRequested.set(true);
            IPermissionGate.PendingPermissionRequest pending = permissionGate.pendingPermissionRequests().getFirst();

            if (allow) {
                permissionGate.resolveSelected(pending.getRequestId(), IPermissionGate.Companion.allowOnce());
            } else {
                permissionGate.resolveCancelled(pending.getRequestId());
            }
        });

        String output = acpTooling.bash(sessionId, command, 5000L, "test command", runInBackground);
        assertThat(permissionRequested.get()).isTrue();
        return output;
    }

    private String extractBashId(String output) {
        String prefix = "bash_id:";
        int index = output.indexOf(prefix);
        if (index == -1) {
            return "";
        }
        int start = index + prefix.length();
        int end = output.indexOf('\n', start);
        if (end == -1) {
            end = output.length();
        }
        return output.substring(start, end).trim();
    }
}
