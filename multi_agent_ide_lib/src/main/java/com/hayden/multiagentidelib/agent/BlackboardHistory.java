package com.hayden.multiagentidelib.agent;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentidelib.service.RequestEnrichment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Blackboard history management for tracking action inputs and preventing
 * unwanted state accumulation through clearing.
 */
public class BlackboardHistory {

    public static History getBlackboardHistory(OperationContext context) {
        if (context == null) {
            return null;
        }
        return context.last(History.class);
    }

    public static <T> T getLastFromHistory(OperationContext context, Class<T> inputType) {
        History history = getBlackboardHistory(context);
        if (history == null) {
            return null;
        }
        return history.getLastOfType(inputType).orElse(null);
    }

    /**
     * Register an input in the blackboard history and hide it from the blackboard.
     * This should be called at the start of each action instead of using clearBlackboard=true.
     *
     * @param context The operation context
     * @param actionName The name of the action being executed
     * @param input The input object to register and hide
     * @return Updated history with the new entry
     */
    public static History registerAndHideInput(
            OperationContext context,
            String actionName,
            Object input
    ) {
        return registerAndHideInput(context, actionName, input, null);
    }

    /**
     * Register an input in the blackboard history and hide it from the blackboard.
     * This overload allows enriching the input with ContextId and PreviousContext before storing.
     *
     * @param context The operation context
     * @param actionName The name of the action being executed
     * @param input The input object to register and hide
     * @param requestEnrichment Optional enrichment service to set ContextId and PreviousContext
     * @return Updated history with the new entry
     */
    public static History registerAndHideInput(
            OperationContext context,
            String actionName,
            Object input,
            RequestEnrichment requestEnrichment
    ) {
        // Get or create history from context
        History history = getBlackboardHistory(context);

        if (history == null) {
            history = new History();
        }

        // Enrich the input with ContextId and PreviousContext if enrichment service is provided
        Object enrichedInput = input;
        if (requestEnrichment != null && input != null) {
            enrichedInput = requestEnrichment.enrich(input, context);
        }

        // Add the enriched input to history
        History updatedHistory = history.withEntry(actionName, enrichedInput);

        if (enrichedInput != null) {
            context.getAgentProcess().clear();
        }

        // Add updated history back to context
        context.getAgentProcess().addObject(updatedHistory);

        return updatedHistory;
    }

    /**
     * Functional interface for providing contextual prompts based on history.
     * Implementations can augment base prompts with retry/loop information.
     */
    @FunctionalInterface
    public interface PromptProvider {
        String providePrompt(String basePrompt);
        
        static PromptProvider identity() {
            return basePrompt -> basePrompt;
        }
        
        default PromptProvider andThen(PromptProvider after) {
            return basePrompt -> after.providePrompt(this.providePrompt(basePrompt));
        }
    }

    /**
     * Tracks a single historical blackboard state entry.
     * Each entry represents a previous action's input that has been archived.
     */
    public record Entry(
            Instant timestamp,
            String actionName,
            Object input,
            Class<?> inputType
    ) {
    }

    /**
     * The main history container.
     * Instead of clearing the blackboard, inputs are transferred here.
     */
    public record History(
            List<Entry> entries
    ) {
        public History() {
            this(new ArrayList<>());
        }

        boolean detectLoop(OperationContext context, Class<?> inputType, int threshold) {
            return this.detectLoop(inputType, threshold);
        }

        /**
         * Add an entry to the history
         */
        public History withEntry(String actionName, Object input) {
            List<Entry> newEntries = new ArrayList<>(entries);
            newEntries.add(new Entry(
                    Instant.now(),
                    actionName,
                    input,
                    input != null ? input.getClass() : null
            ));
            return new History(newEntries);
        }

        /**
         * Check if we've seen this input type before (indicating a retry/loop)
         */
        public boolean hasSeenType(Class<?> type) {
            return entries.stream()
                    .anyMatch(entry -> entry.inputType() != null && entry.inputType().equals(type));
        }

        /**
         * Count how many times we've seen this input type
         */
        public long countType(Class<?> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && entry.inputType().equals(type))
                    .count();
        }

        /**
         * Get all entries of a specific type
         */
        @SuppressWarnings("unchecked")
        public <T> List<T> getEntriesOfType(Class<T> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && entry.inputType().equals(type))
                    .map(entry -> (T) entry.input())
                    .collect(Collectors.toList());
        }

        /**
         * Get the most recent entry of a specific type
         */
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getLastOfType(Class<T> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && entry.inputType().equals(type))
                    .map(entry -> (T) entry.input())
                    .reduce((first, second) -> second);
        }

        /**
         * Check if this is a retry of a specific action
         */
        public boolean isRetry(String actionName) {
            return entries.stream()
                    .anyMatch(entry -> entry.actionName().equals(actionName));
        }

        /**
         * Get retry count for a specific action
         */
        public long getRetryCount(String actionName) {
            return entries.stream()
                    .filter(entry -> entry.actionName().equals(actionName))
                    .count();
        }

        /**
         * Check if we're in a loop by detecting repeated patterns
         */
        public boolean detectLoop(Class<?> type, int threshold) {
            return countType(type) >= threshold;
        }

        /**
         * Get a summary of all historical entries
         */
        public String getSummary() {
            if (entries.isEmpty()) {
                return "No historical entries";
            }

            Map<String, Long> actionCounts = entries.stream()
                    .collect(Collectors.groupingBy(
                            Entry::actionName,
                            Collectors.counting()
                    ));

            StringBuilder summary = new StringBuilder("History Summary:\n");
            actionCounts.forEach((action, count) ->
                    summary.append(String.format("  - %s: %d attempts\n", action, count))
            );

            return summary.toString();
        }

        public Object last() {
            return Optional.ofNullable(entries.getLast()).flatMap(e -> Optional.ofNullable(e.input));
        }
    }
}
