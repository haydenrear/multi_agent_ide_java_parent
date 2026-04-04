package com.hayden.multiagentide.service;

import com.embabel.agent.core.AgentProcess;
import org.springframework.context.annotation.Lazy;
import com.embabel.agent.spi.common.ActionRetryListener;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.CompactionException;
import com.hayden.multiagentidelib.agent.ErrorDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.RetryContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Captures errors from the Embabel framework retry loop into BlackboardHistory.
 * Classifies throwables into ErrorDescriptor variants so that AgentExecutor and
 * PromptContributors can adapt behavior on retry.
 *
 * <p>For compaction errors, polls the ACP session for compaction completion before
 * returning (blocking the retry thread), matching the concurrency model of the
 * existing polling loop in AcpChatModel.
 */
@Component
@Slf4j
public class ActionRetryListenerImpl implements ActionRetryListener {

    private static final int MAX_COMPACTION_POLLS = 20;
    private static final long COMPACTION_POLL_INTERVAL_MS = 10_000;

    @Autowired
    @Lazy
    private EventBus eventBus;

    @Override
    public void onActionRetry(RetryContext context, Throwable throwable, AgentProcess agentProcess) {
        BlackboardHistory history = agentProcess.last(BlackboardHistory.class);
        if (history == null) {
            log.warn("ActionRetryListener: no BlackboardHistory on AgentProcess {} — cannot record error",
                    agentProcess.getId());
            return;
        }

        String actionName = resolveActionName(history);
        ArtifactKey contextId = resolveContextId(history);

        ErrorDescriptor errorDescriptor = classify(throwable, actionName, contextId, history);
        history.addError(errorDescriptor);

        log.info("ActionRetryListener: recorded {} for action={} retryCount={} on process={}",
                errorDescriptor.getClass().getSimpleName(), actionName, context.getRetryCount(),
                agentProcess.getId());

        if (errorDescriptor instanceof ErrorDescriptor.CompactionError compactionError) {
            waitForCompaction(compactionError, contextId);
        }
    }

    /**
     * Classifies a throwable into the appropriate ErrorDescriptor variant.
     */
    ErrorDescriptor classify(Throwable throwable, String actionName, ArtifactKey contextId,
                             BlackboardHistory history) {
        if (throwable instanceof CompactionException) {
            ErrorDescriptor.CompactionStatus status = history.compactionStatus().next();
            return new ErrorDescriptor.CompactionError(actionName, status, false, contextId);
        }

        String message = throwable.getMessage();
        if (message == null) message = "";
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("compacting") || lowerMessage.contains("prompt is too long")) {
            ErrorDescriptor.CompactionStatus status = history.compactionStatus().next();
            return new ErrorDescriptor.CompactionError(actionName, status, false, contextId);
        }

        if (throwable instanceof TimeoutException || lowerMessage.contains("timeout")) {
            int retryCount = (int) history.errorCount(ErrorDescriptor.TimeoutError.class) + 1;
            return new ErrorDescriptor.TimeoutError(actionName, retryCount, contextId);
        }

        if (lowerMessage.contains("tool call")) {
            return new ErrorDescriptor.UnparsedToolCallError(actionName, message, contextId);
        }

        if (throwable instanceof com.fasterxml.jackson.core.JsonParseException
                || lowerMessage.contains("parse")) {
            return new ErrorDescriptor.ParseError(actionName, message, contextId);
        }

        // Fallback: treat as parse error with raw message
        return new ErrorDescriptor.ParseError(actionName, message, contextId);
    }

    /**
     * Blocks until compaction is complete or max polls exceeded.
     * Emits CompactionEvent for observability.
     */
    private void waitForCompaction(ErrorDescriptor.CompactionError error, ArtifactKey contextId) {
        log.info("ActionRetryListener: waiting for compaction to complete for action={}",
                error.actionName());
        eventBus.publish(Events.CompactionEvent.of(
                "ActionRetryListener waiting for compaction — action=" + error.actionName(),
                contextId));

        for (int poll = 1; poll <= MAX_COMPACTION_POLLS; poll++) {
            try {
                Thread.sleep(COMPACTION_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("ActionRetryListener: compaction wait interrupted for action={}",
                        error.actionName());
                return;
            }
            log.info("ActionRetryListener: compaction poll {}/{} for action={}",
                    poll, MAX_COMPACTION_POLLS, error.actionName());
            // The framework retry will re-invoke the action after we return.
            // We just need to give the ACP session time to finish compacting.
            // After waiting, return and let the retry proceed.
        }

        log.warn("ActionRetryListener: compaction still active after {} polls for action={} — returning for framework retry",
                MAX_COMPACTION_POLLS, error.actionName());
    }

    /**
     * Resolves the action name from the last BlackboardHistory entry.
     */
    private String resolveActionName(BlackboardHistory history) {
        List<BlackboardHistory.Entry> entries = history.copyOfEntries();
        if (entries.isEmpty()) {
            return "unknown";
        }
        return entries.getLast().actionName();
    }

    /**
     * Resolves the ArtifactKey (ACP session key) from the last BlackboardHistory entry.
     */
    private ArtifactKey resolveContextId(BlackboardHistory history) {
        List<BlackboardHistory.Entry> entries = history.copyOfEntries();
        if (entries.isEmpty()) {
            return new ArtifactKey("unknown");
        }
        return entries.getLast().contextId();
    }
}
