package com.hayden.multiagentide.config;

import com.hayden.multiagentidelib.agent.AgentType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "llm.model-selection")
public class LlmModelSelectionProperties {

    private String defaultModel;

    private Map<String, String> byAgentType = new HashMap<>();

    private Map<String, String> byTemplate = new HashMap<>();

    /**
     * Resolve model name for a given agent type and template.
     * Priority: byTemplate > byAgentType > defaultModel > null.
     */
    public String resolve(AgentType agentType, String templateName) {
        if (templateName != null && byTemplate.containsKey(templateName)) {
            return byTemplate.get(templateName);
        }
        if (agentType != null && byAgentType.containsKey(agentType.wireValue())) {
            return byAgentType.get(agentType.wireValue());
        }
        return defaultModel;
    }
}
