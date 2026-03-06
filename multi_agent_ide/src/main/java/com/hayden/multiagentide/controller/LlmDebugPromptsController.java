package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.service.PromptFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm-debug/prompts")
@RequiredArgsConstructor
public class LlmDebugPromptsController {

    private final PromptFileService promptFileService;

    @GetMapping
    public List<PromptFileService.PromptFileReference> discover() {
        return promptFileService.discover();
    }

    @PostMapping
    public PromptFileService.PromptChangeResult add(@RequestBody AddPromptRequest request) {
        return promptFileService.addPrompt(new PromptFileService.AddPromptRequest(
                request.promptKey(),
                request.path(),
                request.content()
        ));
    }

    @PostMapping("/update")
    public PromptFileService.PromptChangeResult update(@RequestBody UpdatePromptRequest request) {
        return promptFileService.updatePrompt(request.promptKey(), request.content());
    }

    public record AddPromptRequest(String promptKey, String path, String content) {
    }

    public record UpdatePromptRequest(String promptKey, String content) {
    }
}
