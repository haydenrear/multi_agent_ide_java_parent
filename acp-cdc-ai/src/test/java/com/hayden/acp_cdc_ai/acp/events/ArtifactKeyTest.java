package com.hayden.acp_cdc_ai.acp.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactKeyTest {

    @Test
    public void testArtifactKey() {
        var c = ArtifactKey.createRoot();
        var child = c.createChild();
        var child5 = c.createChild().createChild().createChild();

        assertThat(c.value()).isEqualTo(child.root().value());
        assertThat(c.value()).isEqualTo(child5.root().value());
    }

}
