package com.hayden.multiagentide.filter.validation;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.controller.dto.PolicyRegistrationRequest;
import com.hayden.multiagentide.propagation.controller.dto.PropagatorRegistrationRequest;
import com.hayden.multiagentide.propagation.validation.PropagatorSemanticValidator;
import com.hayden.multiagentide.transformation.controller.dto.TransformerRegistrationRequest;
import com.hayden.multiagentide.transformation.validation.TransformerSemanticValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAutomationLayerValidationTest {

    private final PolicySemanticValidator policySemanticValidator = new PolicySemanticValidator();
    private final PropagatorSemanticValidator propagatorSemanticValidator = new PropagatorSemanticValidator();
    private final TransformerSemanticValidator transformerSemanticValidator = new TransformerSemanticValidator();

    @Test
    void policyValidator_rejectsInternalAutomationLayer() {
        PolicyRegistrationRequest request = PolicyRegistrationRequest.builder()
                .name("policy")
                .description("policy")
                .sourcePath("policy.json")
                .priority(1)
                .executor(Map.of("executorType", "JAVA_FUNCTION", "beanName", "bean", "methodName", "method"))
                .layerBindings(List.of(new PolicyRegistrationRequest.LayerBindingRequest(
                        "ai-propagator/propagate-action",
                        "WORKFLOW_AGENT_ACTION",
                        "propagate-action",
                        true,
                        false,
                        false,
                        false,
                        "PATH",
                        "EXACT",
                        "foo",
                        "PROMPT_CONTRIBUTOR"
                )))
                .build();

        assertThat(policySemanticValidator.validate(FilterEnums.FilterKind.AI_PATH, request))
                .contains("layerBindings[0]: layerId cannot target internal automation layers");
    }

    @Test
    void propagatorValidator_rejectsInternalAutomationLayer() {
        PropagatorRegistrationRequest request = PropagatorRegistrationRequest.builder()
                .name("propagator")
                .description("propagator")
                .sourcePath("propagator.json")
                .priority(1)
                .layerBindings(List.of(new PropagatorRegistrationRequest.LayerBindingRequest(
                        "ai-filter/filter-action",
                        "WORKFLOW_AGENT_ACTION",
                        "filter-action",
                        true,
                        false,
                        false,
                        false,
                        "PATH",
                        "EXACT",
                        "foo",
                        "ACTION_REQUEST"
                )))
                .build();

        assertThat(propagatorSemanticValidator.validate(request))
                .contains("layerBindings[0]: layerId cannot target internal automation layers");
    }

    @Test
    void transformerValidator_rejectsInternalAutomationLayer() {
        TransformerRegistrationRequest request = TransformerRegistrationRequest.builder()
                .name("transformer")
                .description("transformer")
                .sourcePath("transformer.json")
                .priority(1)
                .layerBindings(List.of(new TransformerRegistrationRequest.LayerBindingRequest(
                        "ai-transformer/transform-controller-response",
                        "WORKFLOW_AGENT_ACTION",
                        "transform-controller-response",
                        true,
                        false,
                        false,
                        false,
                        "PATH",
                        "EXACT",
                        "foo",
                        "CONTROLLER_ENDPOINT_RESPONSE"
                )))
                .build();

        assertThat(transformerSemanticValidator.validate(request))
                .contains("layerBindings[0]: layerId cannot target internal automation layers");
    }
}
