package com.hayden.acp_cdc_ai.repository;

import com.hayden.acp_cdc_ai.sandbox.SandboxContext;import lombok.Builder;
import lombok.With;import lombok.experimental.Delegate;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@With
@Builder(toBuilder = true)
public record RequestContext(
        String sessionId,
        @Delegate SandboxContext sandboxContext,
        Map<String, String> metadata
) {


    public RequestContext {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

}
