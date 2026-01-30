package com.hayden.multiagentide.integration;

import com.embabel.agent.core.AgentPlatform;
import com.hayden.multiagentide.agent.*;
import com.hayden.multiagentide.service.InterruptService;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.acp_cdc_ai.acp.AcpChatModel;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WorkflowAgent that test complete workflow paths.
 * 
 * These tests:
 * 1. Mock AcpChatModel to return JSON responses that guide routing
 * 2. Run the real WorkflowAgent through AgentPlatform
 * 3. Verify the workflow executes correctly via:
 *    - WorkflowGraphService method calls (verify actions were invoked)
 *    - TestEventListener events
 *    - Final agent process status
 * 
 * The Embabel planner automatically routes between actions based on SomeOf return types.
 * We verify different workflow paths by controlling the AcpChatModel responses.
 */
@SpringBootTest
class WorkflowAgentIntegrationTest extends AgentTestBase {

    @Autowired
    private AgentPlatform agentPlatform;

    @MockitoBean
    private AcpChatModel acpChatModel;

    @Autowired
    private WorkflowGraphService workflowGraphService;

    @Autowired
    private InterruptService interruptService;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private TestEventListener testEventListener;

    @TestConfiguration
    static class TestConfig {
        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    @BeforeEach
    void setUp() {
        testEventListener.clear();
        reset(acpChatModel, workflowGraphService, interruptService, eventBus);
    }
        
}
