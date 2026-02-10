package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.cli.CliEventFormatter.CliEventArgs;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallRendererTest {

    private static final CliEventArgs SUMMARY_ARGS = new CliEventArgs(160, null);
    private static final CliEventArgs DETAIL_ARGS = new CliEventArgs(20_000, null, true);

    private static Events.ToolCallEvent toolCall(String title, String phase, String status,
                                                  Object rawInput, Object rawOutput) {
        return new Events.ToolCallEvent(
                "evt-1", Instant.now(), "node-1", "tc-1",
                title, "function", status, phase,
                List.of(), List.of(),
                rawInput, rawOutput
        );
    }

    // ── Factory routing ────────────────────────────────────────────────

    @Nested
    class FactoryRouting {

        @ParameterizedTest
        @ValueSource(strings = {"read", "write", "edit"})
        void fileTools(String name) {
            var renderer = ToolCallRendererFactory.rendererFor(toolCall(name, "START", "running", null, null));
            assertThat(renderer).isInstanceOf(ToolCallRenderer.FileToolRenderer.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"bash", "BashOutput", "killShell"})
        void scriptTools(String name) {
            var renderer = ToolCallRendererFactory.rendererFor(toolCall(name, "START", "running", null, null));
            assertThat(renderer).isInstanceOf(ToolCallRenderer.ScriptToolRenderer.class);
        }

        @Test
        void messageTools() {
            var renderer = ToolCallRendererFactory.rendererFor(toolCall("AskUserQuestionTool", "ARGS", "pending", null, null));
            assertThat(renderer).isInstanceOf(ToolCallRenderer.MessageToolRenderer.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"unknown_tool", "grep", "SearchFiles"})
        void unknownToolsFallToGeneric(String name) {
            var renderer = ToolCallRendererFactory.rendererFor(toolCall(name, "START", "running", null, null));
            assertThat(renderer).isInstanceOf(ToolCallRenderer.GenericToolRenderer.class);
        }
    }

    // ── FileToolRenderer ───────────────────────────────────────────────

    @Nested
    class FileToolRendererTests {

        private final ToolCallRenderer renderer = new ToolCallRenderer.FileToolRenderer();

        @Test
        void summaryShowsFilePath() {
            String input = """
                    {"file_path": "/src/main/Foo.java", "content": "hello"}""";
            var event = toolCall("read", "RESULT", "completed", input, "file contents...");
            String summary = renderer.formatSummary(SUMMARY_ARGS, event);

            assertThat(summary).contains("file=/src/main/Foo.java");
            assertThat(summary).contains("tool=read");
        }

        @Test
        void summaryFallsBackToUnknownWhenNoPath() {
            var event = toolCall("read", "START", "running", "{}", null);
            String summary = renderer.formatSummary(SUMMARY_ARGS, event);

            assertThat(summary).contains("file=unknown");
        }

        @Test
        void detailShowsDiffForEdit() {
            String input = """
                    {"file_path": "/src/Foo.java", "old_string": "int x = 1;", "new_string": "int x = 2;"}""";
            var event = toolCall("edit", "RESULT", "completed", input, "ok");
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("File: /src/Foo.java");
            assertThat(detail).contains("--- Diff ---");
            assertThat(detail).contains("- int x = 1;");
            assertThat(detail).contains("+ int x = 2;");
        }

        @Test
        void detailShowsContentForWrite() {
            String input = """
                    {"file_path": "/src/Bar.java", "content": "public class Bar {}"}""";
            var event = toolCall("write", "RESULT", "completed", input, "ok");
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("File: /src/Bar.java");
            assertThat(detail).contains("--- Content ---");
            assertThat(detail).contains("public class Bar {}");
        }

        @Test
        void detailShowsOutputForRead() {
            String input = """
                    {"file_path": "/src/Baz.java"}""";
            var event = toolCall("read", "RESULT", "completed", input, "package com.example;\nclass Baz {}");
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("File: /src/Baz.java");
            assertThat(detail).contains("--- Output ---");
            assertThat(detail).contains("class Baz {}");
        }
    }

    // ── ScriptToolRenderer ─────────────────────────────────────────────

    @Nested
    class ScriptToolRendererTests {

        private final ToolCallRenderer renderer = new ToolCallRenderer.ScriptToolRenderer();

        @Test
        void summaryShowsCommand() {
            String input = """
                    {"command": "git status", "timeout": 30000}""";
            var event = toolCall("bash", "RESULT", "completed", input, "On branch main");
            String summary = renderer.formatSummary(SUMMARY_ARGS, event);

            assertThat(summary).contains("cmd=git status");
            assertThat(summary).contains("tool=bash");
        }

        @Test
        void summaryShowsShellIdForBashOutput() {
            String input = """
                    {"task_id": "shell-42", "block": true}""";
            var event = toolCall("BashOutput", "RESULT", "completed", input, "build output...");
            String summary = renderer.formatSummary(SUMMARY_ARGS, event);

            assertThat(summary).contains("shell=shell-42");
        }

        @Test
        void detailShowsCommandAndOutput() {
            String input = """
                    {"command": "ls -la", "description": "List files", "timeout": 5000}""";
            var event = toolCall("bash", "RESULT", "completed", input, "total 32\ndrwxr-xr-x  5 user");
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("Command: ls -la");
            assertThat(detail).contains("Description: List files");
            assertThat(detail).contains("Timeout: 5000");
            assertThat(detail).contains("--- Output ---");
            assertThat(detail).contains("drwxr-xr-x");
        }
    }

    // ── MessageToolRenderer ────────────────────────────────────────────

    @Nested
    class MessageToolRendererTests {

        private final ToolCallRenderer renderer = new ToolCallRenderer.MessageToolRenderer();

        @Test
        void summaryShowsMessage() {
            var event = toolCall("AskUserQuestionTool", "ARGS", "pending",
                    "What database should we use?", null);
            String summary = renderer.formatSummary(SUMMARY_ARGS, event);

            assertThat(summary).contains("message=What database should we use?");
        }

        @Test
        void detailShowsQuestionAndResponse() {
            var event = toolCall("AskUserQuestionTool", "RESULT", "completed",
                    "What database should we use?", "PostgreSQL please");
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("What database should we use?");
            assertThat(detail).contains("--- Response ---");
            assertThat(detail).contains("PostgreSQL please");
        }
    }

    // ── GenericToolRenderer ────────────────────────────────────────────

    @Nested
    class GenericToolRendererTests {

        private final ToolCallRenderer renderer = new ToolCallRenderer.GenericToolRenderer();

        @Test
        void summaryPreservesBackwardCompatibleFormat() {
            var event = toolCall("custom_tool", "START", "running",
                    """
                    {"key": "value"}""", null);
            String summary = renderer.formatSummary(SUMMARY_ARGS, event);

            assertThat(summary).contains("tool=custom_tool");
            assertThat(summary).contains("kind=function");
            assertThat(summary).contains("status=running");
            assertThat(summary).contains("phase=START");
        }

        @Test
        void detailPrettyPrintsJson() {
            var event = toolCall("custom_tool", "RESULT", "completed",
                    """
                    {"key":"value","nested":{"a":1}}""",
                    """
                    {"result":"ok"}""");
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("--- Input ---");
            assertThat(detail).contains("--- Output ---");
            // Pretty-printed JSON should have indentation
            assertThat(detail).contains("\"key\" : \"value\"");
        }

        @Test
        void detailHandlesNullInputOutput() {
            var event = toolCall("custom_tool", "START", "running", null, null);
            String detail = renderer.formatDetail(DETAIL_ARGS, event);

            assertThat(detail).contains("--- Input ---");
            assertThat(detail).contains("none");
        }
    }

    // ── Integration with CliEventFormatter ──────────────────────────────

    @Nested
    class FormatterIntegration {

        private final CliEventFormatter formatter = new CliEventFormatter(new ArtifactKeyFormatter());

        @Test
        void formatDelegatesToFileRenderer() {
            String input = """
                    {"file_path": "/src/Foo.java"}""";
            var event = toolCall("read", "RESULT", "completed", input, "contents");
            String result = formatter.format(event);

            assertThat(result).contains("[TOOL]");
            assertThat(result).contains("file=/src/Foo.java");
        }

        @Test
        void formatDelegatesToScriptRenderer() {
            String input = """
                    {"command": "git log --oneline"}""";
            var event = toolCall("bash", "RESULT", "completed", input, "abc123 commit");
            String result = formatter.format(event);

            assertThat(result).contains("[TOOL]");
            assertThat(result).contains("cmd=git log --oneline");
        }

        @Test
        void prettyPrintUsesDetailFormat() {
            String input = """
                    {"file_path": "/src/Foo.java", "old_string": "x", "new_string": "y"}""";
            var event = toolCall("edit", "RESULT", "completed", input, "ok");
            String result = formatter.format(new CliEventArgs(20_000, event, true));

            assertThat(result).contains("--- Diff ---");
            assertThat(result).contains("- x");
            assertThat(result).contains("+ y");
        }
    }
}
