package com.hayden.multiagentide.controller;

import jakarta.validation.Valid;
import com.hayden.multiagentide.service.PromptFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompts", description = "Manage LLM prompt files")
public class LlmDebugPromptsController {

    private final PromptFileService promptFileService;

    @GetMapping
    @Operation(summary = "Discover all registered prompt files")
    public List<PromptFileService.PromptFileReference> discover() {
        return promptFileService.discover();
    }

    @PostMapping
    @Operation(summary = "Add a new prompt file")
    public PromptFileService.PromptChangeResult add(@RequestBody @Valid AddPromptRequest request) {
        return promptFileService.addPrompt(new PromptFileService.AddPromptRequest(
                request.promptKey(),
                request.path(),
                request.content()
        ));
    }

    @PostMapping("/update")
    @Operation(summary = "Update the content of an existing prompt file")
    public PromptFileService.PromptChangeResult update(@RequestBody @Valid UpdatePromptRequest request) {
        return promptFileService.updatePrompt(request.promptKey(), request.content());
    }

    public record AddPromptRequest(String promptKey, String path, String content) {
    }

    public record UpdatePromptRequest(String promptKey, String content) {
    }
}
