package com.hayden.utilitymodule.acp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for ACP model provider.
 */
@ConfigurationProperties(prefix = "multi-agent-embabel.acp")
@Data
public class AcpModelProperties {

    private String transport = "stdio";
    private String command;
    private String args;
    private String workingDirectory;
    private String endpoint;
    private String apiKey;
    private String authMethod;
    private Map<String, String> env = new HashMap<>();

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public Map<String, String> envCopy() {
        return new HashMap<>(env);
    }

    public String getTransport() {
        return transport;
    }

    public String getCommand() {
        return command;
    }

    public String getArgs() {
        return args;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public Map<String, String> getEnv() {
        return env;
    }
}
