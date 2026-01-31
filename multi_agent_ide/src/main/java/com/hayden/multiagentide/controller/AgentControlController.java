package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.service.AgentControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentControlController {

    private final AgentControlService agentControlService;

    public record ControlActionRequest(String message) {
    }

    public record ControlActionResponse(String actionId, String status) {
    }

    @PostMapping("/{nodeId}/pause")
    public ControlActionResponse pause(
            @PathVariable String nodeId,
            @RequestBody(required = false) ControlActionRequest request
    ) {
        String actionId = agentControlService.requestPause(nodeId, request != null ? request.message() : null);
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/{nodeId}/stop")
    public ControlActionResponse stop(@PathVariable String nodeId) {
        String actionId = agentControlService.requestStop(nodeId);
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/{nodeId}/resume")
    public ControlActionResponse resume(
            @PathVariable String nodeId,
            @RequestBody(required = false) ControlActionRequest request
    ) {
        String actionId = agentControlService.requestResume(nodeId, request != null ? request.message() : null);
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/{nodeId}/prune")
    public ControlActionResponse prune(
            @PathVariable String nodeId,
            @RequestBody(required = false) ControlActionRequest request
    ) {
        String actionId = agentControlService.requestPrune(nodeId, request != null ? request.message() : null);
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/{nodeId}/branch")
    public ControlActionResponse branch(
            @PathVariable String nodeId,
            @RequestBody(required = false) ControlActionRequest request
    ) {
        String actionId = agentControlService.requestBranch(nodeId, request != null ? request.message() : null);
        return new ControlActionResponse(actionId, "queued");
    }

//   should delete all nodes in tree below also. So if you delete orchestrator node,
//    should delete anything below that node. Also, should delete all events in EventStreamRepository
    @DeleteMapping("/{nodeId}")
    public ControlActionResponse delete(
            @PathVariable String nodeId
    ) {
//        TODO:
        String actionId = agentControlService.delete(nodeId);
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/{nodeId}/review-request")
    public ControlActionResponse reviewRequest(
            @PathVariable String nodeId,
            @RequestBody(required = false) ControlActionRequest request
    ) {
        String actionId = agentControlService.requestReview(nodeId, request != null ? request.message() : null);
        return new ControlActionResponse(actionId, "queued");
    }

}
