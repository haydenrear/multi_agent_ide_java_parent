package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.service.AgentControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentControlController {

    private final AgentControlService agentControlService;

    public record ControlActionRequest(String nodeId, String message) {
    }

    public record ControlActionResponse(String actionId, String status) {
    }

    @PostMapping("/pause")
    public ControlActionResponse pause(@RequestBody ControlActionRequest request) {
        String actionId = agentControlService.requestPause(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/stop")
    public ControlActionResponse stop(@RequestBody ControlActionRequest request) {
        String actionId = agentControlService.requestStop(request.nodeId());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/resume")
    public ControlActionResponse resume(@RequestBody ControlActionRequest request) {
        String actionId = agentControlService.requestResume(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/prune")
    public ControlActionResponse prune(@RequestBody ControlActionRequest request) {
        String actionId = agentControlService.requestPrune(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/branch")
    public ControlActionResponse branch(@RequestBody ControlActionRequest request) {
        String actionId = agentControlService.requestBranch(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

//   should delete all nodes in tree below also. So if you delete orchestrator node,
//    should delete anything below that node. Also, should delete all events in EventStreamRepository
    @PostMapping("/delete")
    public ControlActionResponse delete(@RequestBody ControlActionRequest request) {
//        TODO:
        String actionId = agentControlService.delete(request.nodeId());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/review-request")
    public ControlActionResponse reviewRequest(@RequestBody ControlActionRequest request) {
        String actionId = agentControlService.requestReview(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

}
