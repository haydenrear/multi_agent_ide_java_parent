package com.hayden.multiagentidelib.filter.model;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.prompt.PromptContributor;

/**
 * Marker interface for the domain object a filter operation originates from.
 * Bindings match against this source (name/text/matchOn), not against payload aliases.
 */
public sealed interface FilterSource
        permits FilterSource.PromptContributorSource, FilterSource.GraphEventSource {

    com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn matchOn();

    String matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey key);

    default String describe() {
        String name = matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey.NAME);
        return name == null || name.isBlank() ? "unknown-source" : name;
    }

    static FilterSource promptContributor(PromptContributor contributor) {
        return new PromptContributorSource(contributor);
    }

    static FilterSource graphEvent(Events.GraphEvent event) {
        return new GraphEventSource(event);
    }

    record PromptContributorSource(PromptContributor contributor) implements FilterSource {
        @Override
        public com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn matchOn() {
            return com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn.PROMPT_CONTRIBUTOR;
        }

        @Override
        public String matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey key) {
            if (contributor == null) {
                return null;
            }
            return switch (key) {
                case NAME -> contributor.name();
                case TEXT -> contributor.template();
            };
        }
    }

    record GraphEventSource(Events.GraphEvent event) implements FilterSource {
        @Override
        public com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn matchOn() {
            return com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn.GRAPH_EVENT;
        }

        @Override
        public String matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey key) {
            if (event == null) {
                return null;
            }
            return switch (key) {
                case NAME -> event.getClass().getSimpleName();
                case TEXT -> {
                    String pretty = event.prettyPrint();
                    yield (pretty == null || pretty.isBlank()) ? event.eventType() : pretty;
                }
            };
        }
    }
}
