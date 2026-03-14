package com.hayden.multiagentide.propagation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.propagation.controller.dto.*;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationRepository;
import com.hayden.multiagentide.propagation.validation.PropagatorExecutorValidator;
import com.hayden.multiagentide.propagation.validation.PropagatorSemanticValidator;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.propagation.model.*;
import com.hayden.multiagentidelib.propagation.model.layer.AiPropagatorContext;
import com.hayden.multiagentidelib.propagation.model.layer.DefaultPropagationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropagatorRegistrationService {

    private final PropagatorRegistrationRepository repository;
    private final PropagatorSemanticValidator semanticValidator;
    private final PropagatorExecutorValidator executorValidator;
    private final ObjectMapper objectMapper;
    private final PropagatorAttachableCatalogService attachableCatalogService;

    @Transactional
    public PropagatorRegistrationResponse register(PropagatorRegistrationRequest request) {
        List<String> semanticErrors = semanticValidator.validate(request);
        if (!semanticErrors.isEmpty()) {
            return PropagatorRegistrationResponse.builder().ok(false).message(String.join("; ", semanticErrors)).build();
        }
        JsonNode executorNode = objectMapper.valueToTree(request.executor());
        List<String> executorErrors = executorValidator.validate(executorNode);
        if (!executorErrors.isEmpty()) {
            return PropagatorRegistrationResponse.builder().ok(false).message(String.join("; ", executorErrors)).build();
        }
        Instant now = Instant.now();
        String registrationId = "propagator-" + UUID.randomUUID();
        try {
            ExecutableTool<?, ?, ?> executor = objectMapper.convertValue(request.executor(), ExecutableTool.class);
            Propagator<?, ?, ?> propagator = buildPropagator(registrationId, request, executor, now);
            List<PropagatorLayerBinding> bindings = request.layerBindings().stream()
                    .map(binding -> PropagatorLayerBinding.builder()
                            .layerId(binding.layerId())
                            .enabled(binding.enabled())
                            .includeDescendants(binding.includeDescendants())
                            .isInheritable(binding.isInheritable())
                            .isPropagatedToParent(binding.isPropagatedToParent())
                            .matcherKey(FilterEnums.MatcherKey.valueOf(binding.matcherKey()))
                            .matcherType(FilterEnums.MatcherType.valueOf(binding.matcherType()))
                            .matcherText(binding.matcherText())
                            .matchOn(PropagatorMatchOn.valueOf(binding.matchOn()))
                            .updatedBy("system")
                            .updatedAt(now)
                            .build())
                    .toList();

            repository.save(PropagatorRegistrationEntity.builder()
                    .registrationId(registrationId)
                    .registeredBy("system")
                    .status(request.activate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name())
                    .propagatorKind(request.propagatorKind())
                    .isInheritable(request.isInheritable())
                    .isPropagatedToParent(request.isPropagatedToParent())
                    .propagatorJson(objectMapper.writeValueAsString(propagator))
                    .layerBindingsJson(objectMapper.writeValueAsString(bindings))
                    .activatedAt(request.activate() ? now : null)
                    .build());
            return PropagatorRegistrationResponse.builder().ok(true).registrationId(registrationId)
                    .status(request.activate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name())
                    .message("Propagator registered")
                    .build();
        } catch (Exception e) {
            log.error("Failed to register propagator", e);
            return PropagatorRegistrationResponse.builder().ok(false).message(e.getMessage()).build();
        }
    }

    @Transactional
    public DeactivatePropagatorResponse deactivate(String registrationId) {
        return repository.findByRegistrationId(registrationId)
                .map(entity -> {
                    entity.setStatus(FilterEnums.PolicyStatus.INACTIVE.name());
                    entity.setDeactivatedAt(Instant.now());
                    repository.save(entity);
                    return DeactivatePropagatorResponse.builder().ok(true).registrationId(registrationId).status(entity.getStatus()).message("Propagator deactivated").build();
                })
                .orElseGet(() -> DeactivatePropagatorResponse.builder().ok(false).registrationId(registrationId).message("Propagator not found").build());
    }

    @Transactional
    public PutPropagatorLayerResponse updateLayerBinding(String registrationId, PutPropagatorLayerRequest request) {
        var entityOpt = repository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            return PutPropagatorLayerResponse.builder().ok(false).registrationId(registrationId).message("Propagator not found").build();
        }
        List<String> semanticErrors = semanticValidator.validate(PropagatorRegistrationRequest.builder()
                .name("layer-update")
                .description("layer-update")
                .sourcePath("layer-update")
                .priority(0)
                .layerBindings(List.of(request.layerBinding()))
                .build());
        if (!semanticErrors.isEmpty()) {
            return PutPropagatorLayerResponse.builder()
                    .ok(false)
                    .registrationId(registrationId)
                    .message(String.join("; ", semanticErrors))
                    .build();
        }
        try {
            Instant now = Instant.now();
            List<PropagatorLayerBinding> bindings = objectMapper.readValue(entityOpt.get().getLayerBindingsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, PropagatorLayerBinding.class));
            var lb = request.layerBinding();
            PropagatorLayerBinding replacement = PropagatorLayerBinding.builder()
                    .layerId(lb.layerId())
                    .enabled(lb.enabled())
                    .includeDescendants(lb.includeDescendants())
                    .isInheritable(lb.isInheritable())
                    .isPropagatedToParent(lb.isPropagatedToParent())
                    .matcherKey(FilterEnums.MatcherKey.valueOf(lb.matcherKey()))
                    .matcherType(FilterEnums.MatcherType.valueOf(lb.matcherType()))
                    .matcherText(lb.matcherText())
                    .matchOn(PropagatorMatchOn.valueOf(lb.matchOn()))
                    .updatedBy("system")
                    .updatedAt(now)
                    .build();
            boolean replaced = false;
            java.util.ArrayList<PropagatorLayerBinding> updated = new java.util.ArrayList<>();
            for (PropagatorLayerBinding binding : bindings) {
                if (binding.layerId().equals(replacement.layerId()) && binding.matchOn() == replacement.matchOn()) {
                    updated.add(replacement);
                    replaced = true;
                } else {
                    updated.add(binding);
                }
            }
            if (!replaced) updated.add(replacement);
            entityOpt.get().setLayerBindingsJson(objectMapper.writeValueAsString(updated));
            repository.save(entityOpt.get());
            return PutPropagatorLayerResponse.builder().ok(true).registrationId(registrationId).layerId(replacement.layerId()).message("Binding updated").build();
        } catch (Exception e) {
            return PutPropagatorLayerResponse.builder().ok(false).registrationId(registrationId).message(e.getMessage()).build();
        }
    }

    @Transactional
    public int ensureAutoAiPropagatorsRegistered() {
        Instant now = Instant.now();
        Map<String, PropagatorRegistrationEntity> existingBySourcePath = new LinkedHashMap<>();
        for (PropagatorRegistrationEntity entity : repository.findAll()) {
            extractSourcePath(entity).ifPresent(sourcePath -> existingBySourcePath.put(sourcePath, entity));
        }

        int changed = 0;
        for (ReadPropagatorAttachableTargetsResponse.ActionTarget action : attachableCatalogService.readAttachableTargets().actions()) {
            for (String stage : action.stages()) {
                String sourcePath = autoSourcePath(action.layerId(), stage);
                PropagatorRegistrationRequest request = autoRegistrationRequest(action, stage, sourcePath);
                PropagatorRegistrationEntity existing = existingBySourcePath.get(sourcePath);
                if (existing == null) {
                    saveRegistration("propagator-" + UUID.randomUUID(), request, now, null);
                    changed++;
                    continue;
                }
                saveRegistration(existing.getRegistrationId(), request, now, existing);
                changed++;
            }
        }
        return changed;
    }

    private Propagator<?, ?, ?> buildPropagator(String registrationId, PropagatorRegistrationRequest request, ExecutableTool<?, ?, ?> executor, Instant now) {
        if ("AI_TEXT".equalsIgnoreCase(request.propagatorKind())) {
            return AiTextPropagator.builder()
                    .id(registrationId)
                    .name(request.name())
                    .description(request.description())
                    .sourcePath(request.sourcePath())
                    .executor((ExecutableTool<AgentModels.AiPropagatorRequest, AgentModels.AiPropagatorResult, AiPropagatorContext>) executor)
                    .status(request.activate() ? FilterEnums.PolicyStatus.ACTIVE : FilterEnums.PolicyStatus.INACTIVE)
                    .priority(request.priority())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }
        return TextPropagator.builder()
                .id(registrationId)
                .name(request.name())
                .description(request.description())
                .sourcePath(request.sourcePath())
                .executor((ExecutableTool<String, Object, DefaultPropagationContext>) executor)
                .status(request.activate() ? FilterEnums.PolicyStatus.ACTIVE : FilterEnums.PolicyStatus.INACTIVE)
                .priority(request.priority())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void saveRegistration(String registrationId,
                                  PropagatorRegistrationRequest request,
                                  Instant now,
                                  PropagatorRegistrationEntity existing) {
        try {
            ExecutableTool<?, ?, ?> executor = objectMapper.convertValue(request.executor(), ExecutableTool.class);
            Propagator<?, ?, ?> propagator = buildPropagator(registrationId, request, executor, now);
            List<PropagatorLayerBinding> bindings = request.layerBindings().stream()
                    .map(binding -> PropagatorLayerBinding.builder()
                            .layerId(binding.layerId())
                            .enabled(binding.enabled())
                            .includeDescendants(binding.includeDescendants())
                            .isInheritable(binding.isInheritable())
                            .isPropagatedToParent(binding.isPropagatedToParent())
                            .matcherKey(FilterEnums.MatcherKey.valueOf(binding.matcherKey()))
                            .matcherType(FilterEnums.MatcherType.valueOf(binding.matcherType()))
                            .matcherText(binding.matcherText())
                            .matchOn(PropagatorMatchOn.valueOf(binding.matchOn()))
                            .updatedBy("system")
                            .updatedAt(now)
                            .build())
                    .toList();

            PropagatorRegistrationEntity entity = existing == null ? new PropagatorRegistrationEntity() : existing;
            entity.setRegistrationId(registrationId);
            entity.setRegisteredBy("system");
            entity.setStatus(request.activate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name());
            entity.setPropagatorKind(request.propagatorKind());
            entity.setInheritable(request.isInheritable());
            entity.setPropagatedToParent(request.isPropagatedToParent());
            entity.setPropagatorJson(objectMapper.writeValueAsString(propagator));
            entity.setLayerBindingsJson(objectMapper.writeValueAsString(bindings));
            entity.setActivatedAt(request.activate() ? now : entity.getActivatedAt());
            entity.setDeactivatedAt(request.activate() ? null : now);
            repository.save(entity);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save auto AI propagator registration", e);
        }
    }

    private Optional<String> extractSourcePath(PropagatorRegistrationEntity entity) {
        try {
            return Optional.ofNullable(objectMapper.readTree(entity.getPropagatorJson()).path("sourcePath").asText(null));
        } catch (Exception e) {
            log.debug("Failed to read propagator sourcePath for registration {}", entity.getRegistrationId(), e);
            return Optional.empty();
        }
    }

    private PropagatorRegistrationRequest autoRegistrationRequest(ReadPropagatorAttachableTargetsResponse.ActionTarget action,
                                                                 String stage,
                                                                 String sourcePath) {
        return PropagatorRegistrationRequest.builder()
                .name(autoName(action.layerId(), stage))
                .description("Automatically bootstrapped AI propagator for " + action.layerId() + " [" + stage + "]")
                .sourcePath(sourcePath)
                .propagatorKind("AI_TEXT")
                .priority(100)
                .isInheritable(false)
                .isPropagatedToParent(false)
                .layerBindings(List.of(PropagatorRegistrationRequest.LayerBindingRequest.builder()
                        .layerId(action.layerId())
                        .layerType(FilterEnums.LayerType.WORKFLOW_AGENT_ACTION.name())
                        .layerKey(action.methodName())
                        .enabled(true)
                        .includeDescendants(false)
                        .isInheritable(false)
                        .isPropagatedToParent(false)
                        .matcherKey(FilterEnums.MatcherKey.TEXT.name())
                        .matcherType(FilterEnums.MatcherType.EQUALS.name())
                        .matcherText(action.methodName())
                        .matchOn(stage)
                        .build()))
                .executor(Map.of(
                        "executorType", "AI_PROPAGATOR",
                        "sessionMode", "SAME_SESSION_FOR_ACTION",
                        "registrarPrompt", "Escalate out-of-domain, out-of-distribution, or otherwise controller-relevant request and result payloads."
                ))
                .activate(true)
                .build();
    }

    private String autoSourcePath(String layerId, String stage) {
        return "auto://ai-propagator/" + layerId + "/" + stage;
    }

    private String autoName(String layerId, String stage) {
        return "auto-ai-propagator-" + layerId.replace('/', '-') + "-" + stage.toLowerCase();
    }
}
