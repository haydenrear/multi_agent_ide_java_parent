package com.hayden.multiagentide.integration;

import com.hayden.multiagentide.service.PromptFileService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptDebugLoopIntegrationTest {

    @Test
    void discoverAddAndUpdatePromptThroughService() throws Exception {
        Path workspace = Files.createTempDirectory("prompt-loop-workspace");
        Path promptRoot = workspace.resolve("prompts").resolve("workflow");
        Files.createDirectories(promptRoot);

        PromptFileService service = new PromptFileService(workspace, promptRoot);

        Path initial = promptRoot.resolve("workflow_orchestrator.jinja");
        Files.writeString(initial, "initial prompt");

        List<PromptFileService.PromptFileReference> discovered = service.discover();
        assertFalse(discovered.isEmpty());

        PromptFileService.PromptChangeResult addResult = service.addPrompt(
                new PromptFileService.AddPromptRequest(
                        "new_prompt",
                        workspace.resolve("prompts/workflow/new_prompt.jinja").toString(),
                        "new prompt"
                )
        );
        assertEquals("SUCCESS", addResult.result());

        PromptFileService.PromptChangeResult updateResult = service.updatePrompt("new_prompt", "updated prompt");
        assertEquals("SUCCESS", updateResult.result());

        String updated = Files.readString(promptRoot.resolve("new_prompt.jinja"));
        assertEquals("updated prompt", updated);
    }
}
