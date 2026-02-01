package com.hayden.acp_cdc_ai.sandbox;


import com.hayden.acp_cdc_ai.repository.RequestContext;

public interface SandboxTranslationStrategy {

    String providerKey();

    SandboxTranslation translate(RequestContext context);
}
