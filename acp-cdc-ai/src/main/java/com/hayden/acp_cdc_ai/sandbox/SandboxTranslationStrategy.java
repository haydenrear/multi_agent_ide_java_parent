package com.hayden.acp_cdc_ai.sandbox;


import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SandboxTranslationStrategy {

    String providerKey();

    SandboxTranslation translate(RequestContext context, List<String> args, String modelName);

    default SandboxTranslation translate(RequestContext context, List<String> args) {
        return translate(context, args, "DEFAULT");
    }
}
