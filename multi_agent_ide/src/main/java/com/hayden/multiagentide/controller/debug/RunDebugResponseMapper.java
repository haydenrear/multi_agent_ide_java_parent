package com.hayden.multiagentide.controller.debug;

import com.hayden.multiagentide.model.DebugRun;
import com.hayden.multiagentide.service.DebugRunPersistenceValidationService;
import com.hayden.multiagentide.service.DebugRunQueryService;
import org.springframework.stereotype.Component;

@Component
public class RunDebugResponseMapper {

    public DebugRun mapRun(DebugRun run) {
        return run;
    }

    public DebugRunQueryService.DebugRunPage mapRunPage(DebugRunQueryService.DebugRunPage page) {
        return page;
    }

    public DebugRunQueryService.RunTimelinePage mapTimelinePage(DebugRunQueryService.RunTimelinePage page) {
        return page;
    }

    public DebugRunQueryService.ActionResponse mapAction(DebugRunQueryService.ActionResponse response) {
        return response;
    }

    public DebugRunPersistenceValidationService.PersistenceValidationSummary mapValidation(
            DebugRunPersistenceValidationService.PersistenceValidationSummary summary
    ) {
        return summary;
    }
}
