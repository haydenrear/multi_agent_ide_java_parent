package com.hayden.acp_cdc_ai.mcp;

import com.hayden.utilitymodule.concurrent.striped.StripedLockAspect;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = {StripedLockAspect.class, DynamicMcpToolCallbackProvider.class, RequiredProtocolProperties.class})
@EnableAspectJAutoProxy
@ExtendWith(SpringExtension.class)
class DynamicMcpToolCallbackProviderTest {

    @MockitoBean
    private McpClientCommonProperties commonProperties;

    @MockitoBean
    private McpSyncClientConfigurer mcpSyncClientConfigurer;

    @Autowired
    private DynamicMcpToolCallbackProvider dynamicMcpToolCallbackProvider;

    @Test
    public void testIsReentrant() {
        var s = Mockito.mock(McpSyncClient.class);
        dynamicMcpToolCallbackProvider.getClientConcurrentHashMap().put("hello", s);
        dynamicMcpToolCallbackProvider.killClientAndThen("hello", () -> {
            var res = dynamicMcpToolCallbackProvider.buildClient("hello") ;
            return "hello!";
        });
    }

}
