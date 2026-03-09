package com.hayden.multiagentide.filter.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FilterLayerCatalogTest {

    @Test
    void everyActionCanonicalizesToItsMethodName() {
        assertThat(FilterLayerCatalog.actionDefinitions())
                .isNotEmpty()
                .allSatisfy(action -> assertThat(FilterLayerCatalog.canonicalActionName(
                        action.agentName(),
                        action.actionName(),
                        action.methodName()
                )).isEqualTo(action.methodName()));
    }

    @Test
    void everyActionResolvesByAliasAndMethodToTheExpectedLayer() {
        Map<String, List<FilterLayerCatalog.ActionDefinition>> actionsByAlias = FilterLayerCatalog.actionDefinitions().stream()
                .collect(Collectors.groupingBy(
                        action -> action.agentName() + "::" + action.actionName()
                ));

        assertThat(FilterLayerCatalog.actionDefinitions())
                .allSatisfy(action -> assertThat(FilterLayerCatalog.resolveActionLayer(
                        action.agentName(),
                        null,
                        action.methodName()
                )).contains(action.layerId()));

        actionsByAlias.values().stream()
                .filter(group -> group.size() == 1)
                .map(List::getFirst)
                .forEach(action -> assertThat(FilterLayerCatalog.resolveActionLayer(
                        action.agentName(),
                        action.actionName(),
                        null
                )).contains(action.layerId()));
    }

    @Test
    void everyActionHasAnActionLayerAndExpectedRootParent() {
        Set<String> layerIds = FilterLayerCatalog.layerDefinitions().stream()
                .map(FilterLayerCatalog.LayerDefinition::layerId)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(FilterLayerCatalog.actionDefinitions())
                .allSatisfy(action -> {
                    assertThat(action.layerId()).isEqualTo(action.parentLayerId() + "/" + action.methodName());
                    assertThat(layerIds).contains(action.layerId());
                    assertThat(FilterLayerCatalog.resolveRootLayer(action.agentType()))
                            .contains(action.parentLayerId());
                });
    }

    @Test
    void metadataForEveryActionIncludesCanonicalLayerIdentity() {
        assertThat(FilterLayerCatalog.actionDefinitions())
                .allSatisfy(action -> {
                    Map<String, Object> metadata = FilterLayerCatalog.metadataForLlmCall(
                            Map.of(),
                            action.agentName(),
                            action.actionName(),
                            action.methodName(),
                            action.agentType(),
                            null
                    );

                    assertThat(metadata)
                            .containsEntry(FilterLayerCatalog.METADATA_AGENT_NAME, action.agentName())
                            .containsEntry(FilterLayerCatalog.METADATA_ACTION_NAME, action.actionName())
                            .containsEntry(FilterLayerCatalog.METADATA_METHOD_NAME, action.methodName())
                            .containsEntry(FilterLayerCatalog.METADATA_LAYER_ID, action.layerId());
                });
    }
}
