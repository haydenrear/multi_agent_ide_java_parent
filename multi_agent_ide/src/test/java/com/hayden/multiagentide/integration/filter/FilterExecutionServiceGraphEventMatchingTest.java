package com.hayden.multiagentide.integration.filter;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.service.FilterExecutionService;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.policy.PolicyLayerBinding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterExecutionServiceGraphEventMatchingTest {

    @Test
    void graphEventNameCandidatesPreferEventTypeAndKeepClassFallbacks() {
        Events.GoalStartedEvent event = goalStartedEvent();

        FilterSource.GraphEventSource source = new FilterSource.GraphEventSource(event);

        assertThat(source.nameCandidates())
                .contains("GOAL_STARTED", "GoalStartedEvent")
                .doesNotHaveDuplicates();
        assertThat(source.matcherValue(FilterEnums.MatcherKey.NAME)).isEqualTo("GOAL_STARTED");
    }

    @Test
    void exactMatchAcceptsEventTypeOrSimpleClassName() {
        FilterSource.GraphEventSource source = new FilterSource.GraphEventSource(goalStartedEvent());

        assertThat(FilterExecutionService.matchesGraphEventName(binding(FilterEnums.MatcherType.EQUALS, "GOAL_STARTED"), source)).isTrue();
        assertThat(FilterExecutionService.matchesGraphEventName(binding(FilterEnums.MatcherType.EQUALS, "GoalStartedEvent"), source)).isTrue();
    }

    @Test
    void regexMatchAcceptsEventTypeOrSimpleClassName() {
        FilterSource.GraphEventSource source = new FilterSource.GraphEventSource(goalStartedEvent());

        assertThat(FilterExecutionService.matchesGraphEventName(binding(FilterEnums.MatcherType.REGEX, "GOAL_.*"), source)).isTrue();
        assertThat(FilterExecutionService.matchesGraphEventName(binding(FilterEnums.MatcherType.REGEX, ".*StartedEvent"), source)).isTrue();
    }

    private static Events.GoalStartedEvent goalStartedEvent() {
        return new Events.GoalStartedEvent(
                "event-1",
                Instant.parse("2026-03-10T12:00:00Z"),
                "ak:root",
                "goal",
                "repo",
                "main",
                "title",
                List.of("test")
        );
    }

    private static PolicyLayerBinding binding(FilterEnums.MatcherType matcherType, String matcherText) {
        return new PolicyLayerBinding(
                "controller-ui-event-poll",
                true,
                false,
                false,
                false,
                FilterEnums.MatcherKey.NAME,
                matcherType,
                matcherText,
                FilterEnums.MatchOn.GRAPH_EVENT,
                "test",
                Instant.parse("2026-03-10T12:00:00Z")
        );
    }
}
