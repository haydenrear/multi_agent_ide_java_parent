package com.hayden.multiagentide.filter.service;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.*;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.request.RequestDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordEntity;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.filter.model.*;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentidelib.filter.model.executor.*;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import com.hayden.multiagentidelib.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentidelib.filter.model.layer.DefaultPathFilterContext;
import com.hayden.multiagentidelib.filter.model.layer.PromptContributorContext;
import com.hayden.multiagentidelib.filter.model.policy.PolicyLayerBinding;
import com.hayden.multiagentidelib.filter.service.FilterDescriptor;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Executes applicable active filters for a given layer context.
 * Records FilterDecisionRecord entries for each execution.
 * Deterministic ordering: priority ascending, then policy ID tie-breaker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FilterExecutionService {

    private static final String AI_FILTER_AGENT_NAME = "ai-filter";
    private static final String AI_FILTER_ACTION_NAME = "path-filter";
    private static final String AI_FILTER_METHOD_NAME = "runAiFilter";
    private static final String AI_FILTER_TEMPLATE_NAME = "filter/ai_filter";

    private final PolicyDiscoveryService policyDiscoveryService;
    private final FilterDecisionRecordRepository decisionRecordRepository;
    private final ObjectMapper objectMapper;
    private final ExecutionScopeService executionScopeService;
    private final AgentPlatform agentPlatform;
    private final AiFilterSessionResolver aiFilterSessionResolver;

    @Autowired @Lazy
    private EventBus eventBus;

    @Autowired(required = false)
    private List<PromptContextDecorator> promptContextDecorators = new ArrayList<>();

    @Autowired(required = false)
    private List<ToolContextDecorator> toolContextDecorators = new ArrayList<>();

    @Autowired(required = false)
    private List<RequestDecorator> requestDecorators = new ArrayList<>();

    @Autowired(required = false)
    private List<ResultDecorator> resultDecorators = new ArrayList<>();

    @Autowired
    private AiFilterToolHydration aiFilterToolHydration;

    /**
     * Execute all applicable active filters of the requested kinds for a layer against a payload.
     * Returns the final output after all filters have been applied.
     */
    public <T> FilterResult<T> executeFilters(String layerId,
                                              T payload,
                                              FilterContext ctx,
                                              FilterSource source,
                                              Set<FilterEnums.FilterKind> allowedKinds) {
        if (layerId == null || layerId.isBlank() || source == null) {
            return new FilterResult<>(payload, new FilterDescriptor.NoOpFilterDescriptor());
        }

        List<PolicyRegistrationEntity> policies = policyDiscoveryService.getActivePoliciesByLayer(layerId);

        policies.sort(Comparator.comparingInt(this::extractPriority).thenComparing(PolicyRegistrationEntity::getRegistrationId));

        FilterResult<T> current = new FilterResult<>(payload, new FilterDescriptor.NoOpFilterDescriptor());

        for (PolicyRegistrationEntity policy : policies) {
            if (current.t() == null) {
                break;
            }
            FilterEnums.FilterKind kind = parseFilterKind(policy);

            if (kind == null) {
                continue;
            }

            if (!isAllowedKind(kind, allowedKinds)) {
                continue;
            }

            if (!matchesBinding(policy, layerId, source)) {
                continue;
            }

            try {
                AppliedFilterResult applied = applyPolicy(policy, ctx, current.t(), source);
                FilterDescriptor appliedDescriptor = safeDescriptor(applied.descriptor());
                List<Throwable> descriptorErrors = appliedDescriptor.errors();
                Throwable descriptorError = descriptorErrors.isEmpty() ? null : descriptorErrors.getFirst();
                FilterEnums.FilterAction decisionAction =
                        descriptorError == null ? applied.action() : FilterEnums.FilterAction.ERROR;

                current = new FilterResult<>(
                        (T) applied.output(),
                        safeDescriptor(current.descriptor()).and(appliedDescriptor)
                );
                recordDecision(
                        policy,
                        layerId,
                        decisionAction,
                        applied.input(),
                        applied.output(),
                        appliedDescriptor.instructions(),
                        ctx,
                        descriptorError == null ? applied.error() : descriptorError.getMessage()
                );
                if (descriptorError != null) {
                    log.error("Filter execution error descriptor for policy {}", policy.getRegistrationId(), descriptorError);
                    publishFilterErrorEvent(policy.getRegistrationId(), layerId, source, descriptorError);
                }
            } catch (Exception e) {
                log.error("Filter execution failed for policy {}", policy.getRegistrationId(), e);
                publishFilterErrorEvent(policy.getRegistrationId(), layerId, source, e);
                recordDecision(
                        policy,
                        layerId,
                        FilterEnums.FilterAction.ERROR,
                        current.t(),
                        null,
                        List.of(),
                        ctx,
                        e.getMessage()
                );
            }
        }

        return current;
    }

    private AppliedFilterResult applyPolicy(PolicyRegistrationEntity policy,
                                            FilterContext filterContext,
                                            Object payload,
                                            FilterSource source) throws Exception {
        var filter = objectMapper.readValue(policy.getFilterJson(), Filter.class);

        return switch (filter) {
            case PathFilter p -> {
                if (!(payload instanceof String payloadString))  {
                    yield new AppliedFilterResult(payload, payload, FilterEnums.FilterAction.PASSTHROUGH);
                }

                if (!(filterContext instanceof FilterContext.PathFilterContext)) {
                    filterContext = new DefaultPathFilterContext(filterContext == null ? null : filterContext.layerId(), filterContext);
                }

                DefaultPathFilterContext pathFilterContext = (DefaultPathFilterContext) filterContext;

                yield switch (source) {
                    case FilterSource.GraphEventSource ges ->
                            applyPathFilter(policy, p, pathFilterContext, payloadString);
                    case FilterSource.PromptContributorSource pcs ->
                            applyPathFilter(policy, p, pathFilterContext, payloadString);
                };
            }
            case AiPathFilter aiPathFilter -> {
                if (!(payload instanceof String payloadString)) {
                    yield new AppliedFilterResult(payload, payload, FilterEnums.FilterAction.PASSTHROUGH);
                }

                var aiResult = runAiFilter(aiPathFilter, filterContext, payloadString, policy);

                if (aiResult.isEmpty()) {
                    yield new AppliedFilterResult(payload, payload, FilterEnums.FilterAction.PASSTHROUGH);
                }

                AiPathFilter.AiPathFilterResult result = aiResult.get();
                var action = toAction(payloadString, result.r());
                FilterDescriptor policyDescriptor = descriptorForPolicy(
                        policy,
                        aiPathFilter,
                        action,
                        result.instructions(),
                        filterContext,
                        "AI_PATH"
                );

                yield new AppliedFilterResult(
                        payloadString,
                        result.r(),
                        action,
                        policyDescriptor.and(safeDescriptor(result.descriptor()))
                );
            }
        };
    }

    private AppliedFilterResult applyPathFilter(PolicyRegistrationEntity policy,
                                                PathFilter pathFilter,
                                                DefaultPathFilterContext filterContext,
                                                String payload) {
        var output = pathFilter.apply(payload, filterContext);
        var action = toAction(payload, output.r());
        FilterDescriptor policyDescriptor = descriptorForPolicy(
                policy,
                pathFilter,
                action,
                output.instructions(),
                filterContext,
                "PATH"
        );

        return new AppliedFilterResult(
                payload,
                output.r(),
                action,
                policyDescriptor.and(safeDescriptor(output.descriptor()))
        );
    }


    private Optional<AiPathFilter.AiPathFilterResult> runAiFilter(AiPathFilter p,
                                                                  FilterContext filterContext,
                                                                  String payload,
                                                                  PolicyRegistrationEntity policy) {
        var aiExecutor = p.executor();
        aiFilterToolHydration.hydrate(aiExecutor);


        PromptContext promptContext = getPromptContext(filterContext);
        OperationContext operationContext = getOpContext(filterContext, promptContext);

        if (promptContext.operationContext() == null)
            promptContext = promptContext.toBuilder().operationContext(operationContext).build();

        if (promptContext == null || operationContext == null) {
            log.warn("AI filter for policy {} skipped: no PromptContext/OperationContext available in filter context.",
                    policy.getRegistrationId());
            return Optional.empty();
        }

        AgentModels.AgentRequest decoratedContextRequest = AgentInterfaces.decorateRequest(
                promptContext.currentRequest(),
                operationContext,
                requestDecorators,
                AI_FILTER_AGENT_NAME,
                AI_FILTER_ACTION_NAME,
                AI_FILTER_METHOD_NAME,
                promptContext.previousRequest()
        );

        AgentModels.AiFilterRequest aiSessionRequest = AgentModels.AiFilterRequest.builder()
                .contextId(aiFilterSessionResolver.resolveSessionKey(
                        policy.getRegistrationId(),
                        aiExecutor.sessionMode(),
                        promptContext))
                .input(payload)
                .goal("AI filter execution for policy " + policy.getRegistrationId())
                .build();

        Map<String, Object> model = buildAiModel(
                aiExecutor,
                payload,
                policy,
                decoratedContextRequest
        );

        String resolvedModelName = aiExecutor.modelRef() == null || aiExecutor.modelRef().isBlank()
                ? "DEFAULT" : aiExecutor.modelRef();

        PromptContext aiPromptContext = promptContext
                .toBuilder()
                .templateName(AI_FILTER_TEMPLATE_NAME)
                .modelName(resolvedModelName)
                .currentRequest(aiSessionRequest)
                .previousRequest(decoratedContextRequest)
                .model(model)
                .build();

        PromptContext decoratedPromptContext = AgentInterfaces.decoratePromptContext(
                aiPromptContext,
                operationContext,
                promptContextDecorators,
                AI_FILTER_AGENT_NAME,
                AI_FILTER_ACTION_NAME,
                AI_FILTER_METHOD_NAME,
                decoratedContextRequest,
                aiSessionRequest
        );

        ToolContext decoratedToolContext = AgentInterfaces.decorateToolContext(
                ToolContext.empty(),
                aiSessionRequest,
                decoratedContextRequest,
                operationContext,
                toolContextDecorators,
                AI_FILTER_AGENT_NAME,
                AI_FILTER_ACTION_NAME,
                AI_FILTER_METHOD_NAME
        );

        FilterContext.AiFilterContext aiFilterContext = FilterContext.AiFilterContext.builder()
                .filterContext(filterContext)
                .templateName(AI_FILTER_TEMPLATE_NAME)
                .promptContext(decoratedPromptContext)
                .model(model)
                .toolContext(decoratedToolContext)
                .responseClass(AgentModels.AiFilterResult.class)
                .context(operationContext)
                .build();

        var result = p.apply(aiSessionRequest, aiFilterContext);

        if (result.res() != null) {
            return Optional.of(
                    result.toBuilder()
                            .res(
                                    AgentInterfaces.decorateResult(
                                            result.res(),
                                            operationContext,
                                            resultDecorators,
                                            AI_FILTER_AGENT_NAME,
                                            AI_FILTER_ACTION_NAME,
                                            AI_FILTER_METHOD_NAME,
                                            aiSessionRequest)
                            ).build());
        }

        return Optional.of(result);
    }

    private PromptContext getPromptContext(FilterContext filterContext) {
        var promptContextBuilder = PromptContext.builder();
        if (filterContext instanceof PromptContributorContext promptContributorContext
                && promptContributorContext.ctx() != null) {
            return promptContributorContext.ctx();
        } else if (filterContext instanceof FilterContext.PathFilterContext pathCtx
                && pathCtx.filterContext() instanceof PromptContributorContext promptContributorContext
                && promptContributorContext.ctx() != null) {
            return promptContributorContext.ctx();
        } else if (filterContext instanceof GraphEventObjectContext graphEventObjectContext) {
            return buildPromptContextFromGraphEvent(graphEventObjectContext);
        } else if (filterContext instanceof FilterContext.PathFilterContext pathCtx
                && pathCtx.filterContext() instanceof GraphEventObjectContext graphEventObjectContext) {
            return buildPromptContextFromGraphEvent(graphEventObjectContext);
        } else {
            log.error("Could not build prompt context successfully for {}.", filterContext);
        }

        return promptContextBuilder.build();
    }

    private record ResolvedAgentContext(
            OperationContext operationContext,
            BlackboardHistory blackboardHistory
    ) {}

    private @Nullable ResolvedAgentContext resolveAgentContext(ArtifactKey key) {
        ArtifactKey searchThrough = key;
        while (searchThrough != null) {
            var proc = agentPlatform.getAgentProcess(searchThrough.value());
            if (proc != null) {
                OperationContext opCtx = AgentInterfaces.buildOpContext(proc, AI_FILTER_AGENT_NAME);
                BlackboardHistory history = opCtx != null
                        ? BlackboardHistory.getEntireBlackboardHistory(opCtx)
                        : null;
                return new ResolvedAgentContext(opCtx, history);
            }
            searchThrough = searchThrough.parent().orElse(null);
        }
        return null;
    }

    private PromptContext buildPromptContextFromGraphEvent(GraphEventObjectContext graphCtx) {
        ArtifactKey key = graphCtx.key();
        if (key == null) {
            return PromptContext.builder().agentType(AgentType.AI_FILTER).build();
        }

        ResolvedAgentContext resolved = resolveAgentContext(key);
        if (resolved == null) {
            return PromptContext.builder()
                    .agentType(AgentType.AI_FILTER)
                    .currentContextId(key)
                    .build();
        }

        AgentModels.AgentRequest lastRequest =
                BlackboardHistory.findLastWorkflowRequest(resolved.blackboardHistory());

        return PromptContext.builder()
                .agentType(AgentType.AI_FILTER)
                .currentContextId(key)
                .blackboardHistory(resolved.blackboardHistory())
                .previousRequest(lastRequest)
                .currentRequest(lastRequest)
                .operationContext(resolved.operationContext())
                .build();
    }

    private @Nullable OperationContext getOpContext(FilterContext filterContext, PromptContext pc) {
        if (pc.operationContext() != null)
            return pc.operationContext();

        return buildOpContext(filterContext);
    }

    private OperationContext buildOpContext(FilterContext filterContext) {
        return switch (filterContext) {
            case PromptContributorContext promptContributorContext ->
                    getOpContext(promptContributorContext.getKey());
            case FilterContext.PathFilterContext pathCtx when pathCtx.filterContext() instanceof PromptContributorContext promptContributorContext ->
                    getOpContext(promptContributorContext.getKey());
            case GraphEventObjectContext graphEventObjectContext ->
                    getOpContext(graphEventObjectContext.getKey());
            case FilterContext.PathFilterContext pathCtx when pathCtx.filterContext() instanceof GraphEventObjectContext graphEventObjectContext ->
                    getOpContext(graphEventObjectContext.getKey());
            default ->
                    getOpContext(filterContext.key());
        };
    }

    private OperationContext getOpContext(ArtifactKey key) {

        ArtifactKey searchThrough = key;

        while (searchThrough != null) {
            var p = tryGetOpContext(searchThrough);
            if (p.isPresent())
                return p.get();

            searchThrough = searchThrough.parent().orElse(null);
        }

        log.warn("Could not resolve agent process for artifact key {}; emitting NodeError.", key);
        publishAgentProcessNotFoundError(key);
        return null;
    }

    public Optional<OperationContext> tryGetOpContext(ArtifactKey key) {
        return Optional.ofNullable(agentPlatform.getAgentProcess(key.value()))
                .map(ap -> AgentInterfaces.buildOpContext(ap, "ai-filter"));
    }

    private Map<String, Object> buildAiModel(AiFilterTool<?, ?> aiExecutor,
                                             String payload,
                                             PolicyRegistrationEntity policy,
                                             AgentModels.AgentRequest decoratedRequest) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("payload", payload);
        model.put("input", payload);
        model.put("policyId", policy.getRegistrationId());
        model.put("policyName", policy.getRegistrationId());
        if (aiExecutor.promptTemplate() != null && !aiExecutor.promptTemplate().isBlank()) {
            model.put("promptTemplate", aiExecutor.promptTemplate());
        }
        if (aiExecutor.registrarPrompt() != null && !aiExecutor.registrarPrompt().isBlank()) {
            model.put("registrarPrompt", aiExecutor.registrarPrompt());
        }
        if (aiExecutor.responseMode() != null && !aiExecutor.responseMode().isBlank()) {
            model.put("responseMode", aiExecutor.responseMode());
        } else {
            model.put("responseMode", "INSTRUCTION_LIST");
        }
        if (aiExecutor.requestModelType() != null && !aiExecutor.requestModelType().isBlank()) {
            model.put("requestModelType", aiExecutor.requestModelType());
        }
        if (aiExecutor.resultModelType() != null && !aiExecutor.resultModelType().isBlank()) {
            model.put("resultModelType", aiExecutor.resultModelType());
        }
        if (aiExecutor.outputSchema() != null) {
            model.put("outputSchema", aiExecutor.outputSchema());
        }
        if (aiExecutor.maxTokens() > 0) {
            model.put("maxTokens", aiExecutor.maxTokens());
        }
        if (decoratedRequest != null) {
            model.put("contextRequest", decoratedRequest.prettyPrint());
        }
        return model;
    }


    private FilterDescriptor descriptorForPolicy(PolicyRegistrationEntity policy,
                                                 Filter<?, ?, ?> filter,
                                                 FilterEnums.FilterAction action,
                                                 List<Instruction> instructions,
                                                 FilterContext filterContext,
                                                 String descriptorType) {
        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                descriptorType,
                policy.getRegistrationId(),
                filter.id(),
                filter.name(),
                policy.getFilterKind(),
                filter.sourcePath(),
                action == null ? null : action.name(),
                filter.executor() == null ? null : filter.executor().executorType().name(),
                buildExecutorDetails(filter.executor(), filterContext),
                instructions == null ? List.of() : instructions
        );

        if ("PATH".equals(descriptorType)) {
            return new FilterDescriptor.InstructionsFilterDescriptor(
                    instructions == null ? List.of() : instructions,
                    List.of(),
                    entry
            );
        }

        return new FilterDescriptor.SerdesFilterDescriptor(
                action == null ? null : action.name(),
                List.of(),
                entry
        );
    }

    private Map<String, String> buildExecutorDetails(ExecutableTool<?, ?, ?> executor, FilterContext filterContext) {
        if (executor == null) {
            return Map.of();
        }

        Map<String, String> details = new LinkedHashMap<>();
        details.put("timeoutMs", String.valueOf(executor.timeoutMs()));
        putIfPresent(details, "configVersion", executor.configVersion());

        switch (executor) {
            case BinaryExecutor<?, ?, ?> binary -> addBinaryDetails(details, binary, filterContext);
            case PythonExecutor<?, ?, ?> python -> addPythonDetails(details, python, filterContext);
            case JavaFunctionExecutor<?, ?, ?> javaFn -> addJavaFunctionDetails(details, javaFn);
            case AiFilterTool ai -> addAiDetails(details, ai);
        }
        return details;
    }

    private void addBinaryDetails(Map<String, String> details,
                                  BinaryExecutor<?, ?, ?> binary,
                                  FilterContext filterContext) {
        if (binary.command() != null && !binary.command().isEmpty()) {
            details.put("command", String.join(" ", binary.command()));
        }
        putIfPresent(details, "workingDirectory", binary.workingDirectory());
        putIfPresent(details, "outputParserRef", binary.outputParserRef());

        Path binaryPath = resolveBinaryPath(binary, filterContext);
        if (binaryPath != null) {
            details.put("binaryPath", binaryPath.toString());
            putIfPresent(details, "binaryHash", hashFile(binaryPath));
        }
    }

    private Path resolveBinaryPath(BinaryExecutor<?, ?, ?> binary, FilterContext filterContext) {
        if (binary.command() == null || binary.command().isEmpty() || binary.command().getFirst() == null) {
            return null;
        }
        String first = binary.command().getFirst();
        Path raw = Paths.get(first);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getBins() != null) {
            return filterContext.filterConfigProperties().getBins().resolve(raw).normalize();
        }
        return raw.normalize();
    }

    private void addPythonDetails(Map<String, String> details,
                                  PythonExecutor<?, ?, ?> python,
                                  FilterContext filterContext) {
        putIfPresent(details, "scriptPath", python.scriptPath());
        putIfPresent(details, "entryFunction", python.entryFunction());
        if (python.runtimeArgsSchema() != null) {
            details.put("runtimeArgsSchemaJson", toJsonSafely(python.runtimeArgsSchema()));
        }

        Path scriptPath = resolvePythonScriptPath(python, filterContext);
        if (scriptPath == null) {
            return;
        }
        details.put("resolvedScriptPath", scriptPath.toString());
        putIfPresent(details, "scriptHash", hashFile(scriptPath));
        putIfPresent(details, "scriptText", readFileText(scriptPath));
    }

    private Path resolvePythonScriptPath(PythonExecutor<?, ?, ?> python, FilterContext filterContext) {
        if (python.scriptPath() == null || python.scriptPath().isBlank()) {
            return null;
        }
        Path raw = Paths.get(python.scriptPath());
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getBins() != null) {
            return filterContext.filterConfigProperties().getBins().resolve(raw).normalize();
        }
        return raw.normalize();
    }

    private void addJavaFunctionDetails(Map<String, String> details,
                                        JavaFunctionExecutor<?, ?, ?> javaFn) {
        putIfPresent(details, "functionRef", javaFn.functionRef());
        putIfPresent(details, "className", javaFn.className());
        putIfPresent(details, "methodName", javaFn.methodName());
    }

    private void addAiDetails(Map<String, String> details,
                              AiFilterTool ai) {
        putIfPresent(details, "modelRef", ai.modelRef());
        putIfPresent(details, "sessionMode", ai.sessionMode() == null ? null : ai.sessionMode().name());
        putIfPresent(details, "sessionKeyOverride", ai.sessionKeyOverride());
        putIfPresent(details, "requestModelType", ai.requestModelType());
        putIfPresent(details, "resultModelType", ai.resultModelType());
        putIfPresent(details, "registrarPrompt", ai.registrarPrompt());
        putIfPresent(details, "includeAgentDecorators", ai.includeAgentDecorators() == null ? null : ai.includeAgentDecorators().toString());
        putIfPresent(details, "controllerModelRef", ai.controllerModelRef());
        putIfPresent(details, "controllerPromptTemplate", ai.controllerPromptTemplate());
        putIfPresent(details, "outputSchemaJson", toJsonSafely(ai.outputSchema()));
        if (ai.promptTemplate() != null) {
            details.put("promptTemplate", ai.promptTemplate());
            details.put("promptTemplateHash", ArtifactHashing.hashText(ai.promptTemplate()));
        }
    }

    private String hashFile(Path path) {
        try {
            if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }
            return ArtifactHashing.hashBytes(Files.readAllBytes(path));
        } catch (Exception e) {
            log.warn("Failed to hash file at {}", path, e);
            return null;
        }
    }

    private String readFileText(Path path) {
        try {
            if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read file text at {}", path, e);
            return null;
        }
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private FilterDescriptor safeDescriptor(FilterDescriptor descriptor) {
        return descriptor == null ? new FilterDescriptor.NoOpFilterDescriptor() : descriptor;
    }

    private void publishFilterErrorEvent(String policyId,
                                         String layerId,
                                         FilterSource source,
                                         Throwable throwable) {
        try {
            String nodeId = resolveNodeId(layerId, source);
            String message = buildFilterErrorMessage(policyId, layerId, throwable);
            eventBus.publish(new Events.NodeErrorEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "Filter Policy Error",
                    Events.NodeType.WORK,
                    message
            ));
        } catch (Exception publishError) {
            log.error("Failed to publish filter error event for policy {}", policyId, publishError);
        }
    }

    private void publishAgentProcessNotFoundError(ArtifactKey key) {
        try {
            String nodeId = key != null ? key.value() : ArtifactKey.createRoot().value();
            eventBus.publish(new Events.NodeErrorEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "AI Filter: Agent Process Not Found",
                    Events.NodeType.WORK,
                    "Could not resolve agent process for artifact key " + nodeId
                            + ". AI filter execution will be skipped for this context."
            ));
        } catch (Exception e) {
            log.error("Failed to publish agent-process-not-found error for key {}", key, e);
        }
    }

    private String resolveNodeId(String layerId, FilterSource source) {
        if (source instanceof FilterSource.GraphEventSource graphEventSource
                && graphEventSource.event() != null
                && graphEventSource.event().nodeId() != null
                && !graphEventSource.event().nodeId().isBlank()) {
            return graphEventSource.event().nodeId();
        }
        EventBus.AgentNodeKey process = EventBus.Process.get();
        if (process != null && process.id() != null && !process.id().isBlank()) {
            return process.id();
        }
        if (layerId != null && !layerId.isBlank()) {
            return layerId;
        }
        return ArtifactKey.createRoot().value();
    }

    private String buildFilterErrorMessage(String policyId, String layerId, Throwable throwable) {
        StringBuilder message = new StringBuilder("Filter policy execution error");
        if (policyId != null && !policyId.isBlank()) {
            message.append(" [policyId=").append(policyId).append("]");
        }
        if (layerId != null && !layerId.isBlank()) {
            message.append(" [layerId=").append(layerId).append("]");
        }
        if (throwable != null) {
            message.append(": ").append(throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
                message.append(" - ").append(throwable.getMessage());
            }
        }
        return message.toString();
    }

    private FilterEnums.FilterAction toAction(Object input, Object output) {
        if (output == null) {
            return FilterEnums.FilterAction.DROPPED;
        }
        if (!sameValue(input, output)) {
            return FilterEnums.FilterAction.TRANSFORMED;
        }
        return FilterEnums.FilterAction.PASSTHROUGH;
    }

    private boolean sameValue(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    private FilterEnums.FilterKind parseFilterKind(PolicyRegistrationEntity policy) {
        String filterKind = policy.getFilterKind();
        if (filterKind == null || filterKind.isBlank()) {
            return null;
        }
        try {
            return FilterEnums.FilterKind.valueOf(filterKind);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown filter kind on policy {}: {}", policy.getRegistrationId(), filterKind);
            return null;
        }
    }

    private boolean matchesBinding(PolicyRegistrationEntity policy, String layerId, FilterSource source) {
        try {
            List<PolicyLayerBinding> bindings = objectMapper.readValue(policy.getLayerBindingsJson(), new TypeReference<>() {});

            return bindings.stream()
                    .filter(b -> b.enabled() && b.layerId().equals(layerId))
                    .filter(b -> b.matchOn() != null && b.matchOn() == source.matchOn())
                    .anyMatch(b -> matchesSource(b, source));
        } catch (Exception e) {
            log.error("Failed to parse bindings for policy {}", policy.getRegistrationId(), e);
            return false;
        }
    }

    private boolean matchesSource(PolicyLayerBinding binding, FilterSource source) {
        String valueToMatch = source.matcherValue(binding.matcherKey());
        if (valueToMatch == null) {
            return true;
        }

        return switch (binding.matcherType()) {
            case EQUALS ->
                    binding.matcherText().equals(valueToMatch);
            case REGEX -> {
                try {
                    yield Pattern.compile(binding.matcherText()).matcher(valueToMatch).matches();
                } catch (Exception e) {
                    log.warn("Invalid regex in matcher: {}", binding.matcherText());
                    yield false;
                }
            }
        };
    }

    private void recordDecision(PolicyRegistrationEntity policy,
                                String layerId,
                                FilterEnums.FilterAction action,
                                Object input,
                                Object output,
                                List<Instruction> appliedInstructions,
                                FilterContext filterContext,
                                String error) {
        try {
            String inputJson = toJsonSafely(input);
            String outputJson = toJsonSafely(output);
            String instructionsJson = toJsonSafely(appliedInstructions);
            FilterDecisionRecordEntity record = FilterDecisionRecordEntity.builder()
                    .decisionId("dec-" + UUID.randomUUID())
                    .policyId(policy.getRegistrationId())
                    .filterType(policy.getFilterKind())
                    .layerId(layerId)
                    .action(action.name())
                    .inputJson(inputJson)
                    .outputJson(outputJson)
                    .appliedInstructionsJson(instructionsJson)
                    .errorMessage(error)
                    .createdAt(Instant.now())
                    .build();

            decisionRecordRepository.save(record);

            var hash = ArtifactHashing.hashMap(Map.of(
                    "inputJson", Optional.ofNullable(inputJson).orElse(""),
                    "outputJson", Optional.ofNullable(outputJson).orElse(""),
                    "instructionJson", Optional.ofNullable(instructionsJson).orElse("")
            ));

            executionScopeService.emitArtifact(
                    Artifact.FilterDecisionRecordArtifact.builder()
                            .hash(hash)
                            .artifactKey(filterContext.key().createChild())
                            .inputJson(inputJson)
                            .outputJson(outputJson)
                            .instructionsJson(instructionsJson)
                            .children(new ArrayList<>())
                            .build(),
                    filterContext.key()
            );

        } catch (Exception e) {
            log.error("Failed to record filter decision", e);
        }
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(String.valueOf(value));
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
    }

    private int extractPriority(PolicyRegistrationEntity policy) {
        try {
            var node = objectMapper.readTree(policy.getFilterJson());
            return node.has("priority") ? node.get("priority").asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isAllowedKind(FilterEnums.FilterKind kind, Set<FilterEnums.FilterKind> allowedKinds) {
        if (allowedKinds == null || allowedKinds.isEmpty()) {
            return true;
        }
        return allowedKinds.contains(kind);
    }

    private record AppliedFilterResult(
            Object input,
            Object output,
            FilterEnums.FilterAction action,
            FilterDescriptor descriptor,
            String error
    ) {

        public AppliedFilterResult(Object input, Object output, FilterEnums.FilterAction action) {
            this(input, output, action, new FilterDescriptor.NoOpFilterDescriptor(), null);
        }

        public AppliedFilterResult(Object input, Object output, FilterEnums.FilterAction action, List<Instruction> instructions) {
            this(input, output, action, new FilterDescriptor.InstructionsFilterDescriptor(instructions, new ArrayList<>()), null);
        }

        public AppliedFilterResult(Object input, Object output, FilterEnums.FilterAction action, FilterDescriptor descriptor) {
            this(input, output, action, descriptor, null);
        }
    }

}
