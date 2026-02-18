package com.hayden.acp_cdc_ai.acp

import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties
import com.hayden.acp_cdc_ai.acp.config.McpProperties
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.acp_cdc_ai.repository.RequestContextRepository
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AcpChatModelToolCallParsingTest {

    private val model = AcpChatModel(
        mock(AcpModelProperties::class.java),
        mock(ChatMemoryContext::class.java),
        mock(AcpSessionManager::class.java),
        mock(McpProperties::class.java),
        mock(IPermissionGate::class.java),
        mock(RequestContextRepository::class.java),
        mock(SandboxTranslationRegistry::class.java)
    )

    @Test
    fun extractsToolNameFromTrailingJsonToolCall() {
        val input = """
            I need to inspect resources first.
            {"tool_name":"platform__list_resources","tool_arguments":"{}"}
        """.trimIndent()

        val parsed = model.extractToolCallNameFromLastStructuredPayload(input)
        assertEquals("platform__list_resources", parsed)
    }

    @Test
    fun ignoresTrailingJsonThatIsNotAToolCall() {
        val input = """
            {"discoveryOrchestratorRequest":{"goal":"ok"}}
        """.trimIndent()

        val parsed = model.extractToolCallNameFromLastStructuredPayload(input)
        assertNull(parsed)
    }

    @Test
    fun extractsToolNameFromToolCallTag() {
        val input = "<tool_call>platform__read_resource({})"

        val parsed = model.extractToolCallNameFromLastStructuredPayload(input)
        assertEquals("platform__read_resource", parsed)
    }
}
