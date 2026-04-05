package com.hayden.multiagentide.model.layer;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GraphEventObjectContextTest {

    @Test
    void usesEventNodeIdDirectlyAsArtifactKey() {
        String nodeId = ArtifactKey.createRoot().value();
        Events.AddMessageEvent event = new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "message"
        );

        GraphEventObjectContext context = new GraphEventObjectContext("controller-ui-event-poll", event);

        assertThat(context.key()).isNotNull();
        assertThat(context.key().value()).isEqualTo(nodeId);
        assertThat(context.key().parent()).isEmpty();
    }
}
