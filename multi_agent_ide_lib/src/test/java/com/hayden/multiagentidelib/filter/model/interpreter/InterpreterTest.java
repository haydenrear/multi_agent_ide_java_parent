package com.hayden.multiagentidelib.filter.model.interpreter;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.acp_cdc_ai.acp.filter.InstructionMatcher;
import com.hayden.acp_cdc_ai.acp.filter.path.JsonPath;
import com.hayden.acp_cdc_ai.acp.filter.path.MarkdownPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterpreterTest {

    // ═══════════════════════════════════════════════════════════════════
    //  RegexInterpreter
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RegexInterpreter")
    class RegexInterpreterTests {

        private final Interpreter interpreter = new Interpreter.RegexInterpreter();

        @Test
        @DisplayName("Replace replaces all occurrences of pattern")
        void replaceAll() {
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("foo"))
                    .value("bar")
                    .order(0)
                    .build();

            var result = interpreter.apply("foo and foo again", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("bar and bar again");
        }

        @Test
        @DisplayName("Set replaces all occurrences of pattern")
        void setAll() {
            var instruction = Instruction.Set.builder()
                    .targetPath(new MarkdownPath("\\d+"))
                    .value("NUM")
                    .order(0)
                    .build();

            var result = interpreter.apply("item 1 and item 2", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("item NUM and item NUM");
        }

        @Test
        @DisplayName("Remove deletes all matches")
        void removeAll() {
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("\\s+extra"))
                    .order(0)
                    .build();

            var result = interpreter.apply("hello extra world extra", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("Invalid regex returns error Result")
        void invalidRegex() {
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("[invalid"))
                    .order(0)
                    .build();

            var result = interpreter.apply("some text", List.of(instruction));

            assertThat(result.isErr()).isTrue();
            assertThat(result.e().get().getMessage()).contains("Invalid regex pattern");
        }

        @Test
        @DisplayName("Multiple instructions applied in order")
        void multipleInstructionsOrdered() {
            var first = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("A"))
                    .value("B")
                    .order(1)
                    .build();
            var second = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("B"))
                    .value("C")
                    .order(2)
                    .build();

            var result = interpreter.apply("A", List.of(second, first));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("C");
        }

        @Test
        @DisplayName("ReplaceIfMatch with REGEX matcher can replace target-path matches")
        void replaceIfMatchRegexOnMatchedTarget() {
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("item \\d+ done"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "\\d+"))
                    .value("X")
                    .order(0)
                    .build();

            var result = interpreter.apply("item 42 done and item 7 done", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("X and X");
        }

        @Test
        @DisplayName("RemoveIfMatch with REGEX matcher can remove target-path matches")
        void removeIfMatchRegexOnMatchedTarget() {
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new MarkdownPath("\\[.*?\\]"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "\\d+"))
                    .order(0)
                    .build();

            var result = interpreter.apply("prefix [abc123def456] suffix", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("prefix  suffix");
        }

        @Test
        @DisplayName("ReplaceIfMatch with EQUALS matcher replaces only matching regions")
        void replaceIfMatchEquals() {
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("\\w+"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "foo"))
                    .value("REPLACED")
                    .order(0)
                    .build();

            var result = interpreter.apply("foo bar foo", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("REPLACED bar REPLACED");
        }

        @Test
        @DisplayName("ReplaceIfMatch with REGEX matcher replaces only matching regions")
        void replaceIfMatchRegex() {
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("\\w+"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "^f"))
                    .value("X")
                    .order(0)
                    .build();

            var result = interpreter.apply("foo bar fizz", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("X bar X");
        }

        @Test
        @DisplayName("RemoveIfMatch removes only regions where matcher evaluates true")
        void removeIfMatch() {
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new MarkdownPath("\\[\\w+\\]"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "bad"))
                    .order(0)
                    .build();

            var result = interpreter.apply("[good] [bad] [ok]", List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo("[good]  [ok]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JsonPathInterpreter
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JsonPathInterpreter")
    class JsonPathInterpreterTests {

        private final Interpreter interpreter = new Interpreter.JsonPathInterpreter();

        @Test
        @DisplayName("Replace sets a value at a JSON path")
        void replaceJsonPath() {
            String json = """
                    {"name":"Alice","age":30}""";
            var instruction = Instruction.Replace.builder()
                    .targetPath(new JsonPath("$.name"))
                    .value("Bob")
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("\"name\":\"Bob\"");
        }

        @Test
        @DisplayName("Remove deletes a JSON path")
        void removeJsonPath() {
            String json = """
                    {"name":"Alice","age":30}""";
            var instruction = Instruction.Remove.builder()
                    .targetPath(new JsonPath("$.age"))
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).contains("\"name\"");
            assertThat(output).doesNotContain("\"age\"");
        }

        @Test
        @DisplayName("Invalid JSON returns error Result")
        void invalidJson() {
            var instruction = Instruction.Remove.builder()
                    .targetPath(new JsonPath("$.foo"))
                    .order(0)
                    .build();

            var result = interpreter.apply("not json {{{", List.of(instruction));

            assertThat(result.isErr()).isTrue();
            assertThat(result.e().get().getMessage()).containsAnyOf("Failed to parse JSON input", "Failed to apply");
        }

        @Test
        @DisplayName("Invalid JSON path returns error Result")
        void invalidJsonPath() {
            String json = """
                    {"name":"Alice"}""";
            var instruction = Instruction.Set.builder()
                    .targetPath(new JsonPath("$$$invalid"))
                    .value("x")
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isErr()).isTrue();
        }

        @Test
        @DisplayName("ReplaceIfMatch with REGEX matcher replaces JSON field value")
        void replaceIfMatchRegex() {
            String json = """
                    {"msg":"hello 123 world 456"}""";
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new JsonPath("$.msg"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "\\d+"))
                    .value("NUM")
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("\"msg\":\"NUM\"");
        }

        @Test
        @DisplayName("RemoveIfMatch with REGEX matcher removes JSON field value")
        void removeIfMatchRegex() {
            String json = """
                    {"msg":"abc123def"}""";
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new JsonPath("$.msg"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "\\d+"))
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).doesNotContain("\"msg\"");
        }

        @Test
        @DisplayName("ReplaceIfMatch replaces value only when matcher matches")
        void replaceIfMatchTrue() {
            String json = """
                    {"status":"error: timeout"}""";
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new JsonPath("$.status"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "error"))
                    .value("CLEARED")
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("\"status\":\"CLEARED\"");
        }

        @Test
        @DisplayName("ReplaceIfMatch is no-op when matcher does not match")
        void replaceIfMatchFalse() {
            String json = """
                    {"status":"ok"}""";
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new JsonPath("$.status"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "error"))
                    .value("CLEARED")
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("\"status\":\"ok\"");
        }

        @Test
        @DisplayName("RemoveIfMatch deletes path when matcher matches")
        void removeIfMatch() {
            String json = """
                    {"name":"Alice","secret":"password123"}""";
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new JsonPath("$.secret"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "password"))
                    .order(0)
                    .build();

            var result = interpreter.apply(json, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).doesNotContain("secret");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MarkdownPathInterpreter
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MarkdownPathInterpreter")
    class MarkdownPathInterpreterTests {

        private final Interpreter interpreter = new Interpreter.MarkdownPathInterpreter();

        // ── Replace ──────────────────────────────────────────────────

        @Test
        @DisplayName("Replace h2 section content")
        void replaceH2() {
            String md = """
                    # Title
                    intro
                    ## Section A
                    old content A
                    ## Section B
                    content B
                    """;
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("## Section A"))
                    .value("new content A\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).contains("## Section A\nnew content A\n");
            assertThat(output).doesNotContain("old content A");
            assertThat(output).contains("## Section B\ncontent B");
        }

        @Test
        @DisplayName("Replace h1 heading section")
        void replaceH1() {
            String md = """
                    # First
                    content1
                    # Second
                    content2
                    """;
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("# First"))
                    .value("replaced\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("# First\nreplaced\n");
            assertThat(result.r().get()).doesNotContain("content1");
        }

        @Test
        @DisplayName("Replace h3 heading section")
        void replaceH3() {
            String md = """
                    ## Parent
                    ### Sub A
                    sub a content
                    ### Sub B
                    sub b content
                    """;
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("### Sub A"))
                    .value("new sub a\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("### Sub A\nnew sub a\n");
            assertThat(result.r().get()).doesNotContain("sub a content");
            assertThat(result.r().get()).contains("### Sub B\nsub b content");
        }

        @Test
        @DisplayName("Replace h4, h5, h6 heading sections")
        void replaceDeepHeadings() {
            String md = """
                    #### Deep A
                    deep a content
                    #### Deep B
                    deep b content
                    """;
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("#### Deep A"))
                    .value("replaced deep\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("#### Deep A\nreplaced deep\n");
            assertThat(result.r().get()).doesNotContain("deep a content");
        }

        // ── Remove ──────────────────────────────────────────────────

        @Test
        @DisplayName("Remove h2 section entirely")
        void removeH2() {
            String md = """
                    # Title
                    ## Remove Me
                    to be removed
                    ## Keep Me
                    keep this
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("## Remove Me"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("Remove Me");
            assertThat(output).doesNotContain("to be removed");
            assertThat(output).contains("## Keep Me\nkeep this");
        }

        @Test
        @DisplayName("Heading match is case-insensitive and whitespace-insensitive")
        void headingMatchIgnoresCaseAndWhitespace() {
            String md = """
                    ##   Debug   Info
                    verbose output
                    ## Keep
                    keep this
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("## debuginfo"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("Debug   Info");
            assertThat(output).doesNotContain("verbose output");
            assertThat(output).contains("## Keep\nkeep this");
        }

        @Test
        @DisplayName("Heading match tolerates path spacing variance")
        void headingPathSpacingVariance() {
            String md = """
                    ## Build Status
                    healthy
                    ## Keep
                    keep content
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("##   build    status   "))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("Build Status");
            assertThat(output).doesNotContain("healthy");
            assertThat(output).contains("## Keep\nkeep content");
        }

        @Test
        @DisplayName("Remove h1 section")
        void removeH1() {
            String md = """
                    # Remove
                    removed content
                    # Keep
                    kept content
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("# Remove"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).doesNotContain("Remove");
            assertThat(result.r().get()).contains("# Keep\nkept content");
        }

        @Test
        @DisplayName("Root remove accepts surrounding whitespace")
        void rootRemoveWithWhitespace() {
            String md = """
                    # Title
                    content
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("   #   "))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEmpty();
        }

        // ── ALL matching headings ────────────────────────────────────

        @Test
        @DisplayName("Replace applies to ALL matching headings, not just the first")
        void replaceAllMatchingHeadings() {
            String md = """
                    ## Item
                    first content
                    ## Other
                    other content
                    ## Item
                    second content
                    ## Item
                    third content
                    """;
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("## Item"))
                    .value("REPLACED\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("first content");
            assertThat(output).doesNotContain("second content");
            assertThat(output).doesNotContain("third content");
            assertThat(output).contains("## Other\nother content");
            long replacedCount = output.lines().filter(l -> l.equals("REPLACED")).count();
            assertThat(replacedCount).isEqualTo(3);
        }

        @Test
        @DisplayName("Remove removes ALL matching headings, not just the first")
        void removeAllMatchingHeadings() {
            String md = """
                    ## Keep
                    keep content
                    ## Dup
                    dup content 1
                    ## Keep2
                    keep2 content
                    ## Dup
                    dup content 2
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("## Dup"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("Dup");
            assertThat(output).doesNotContain("dup content");
            assertThat(output).contains("## Keep\nkeep content");
            assertThat(output).contains("## Keep2\nkeep2 content");
        }

        // ── ReplaceIfMatch (regex) ───────────────────────────────────

        @Test
        @DisplayName("ReplaceIfMatch with REGEX matcher replaces section content, keeping heading")
        void replaceIfMatchRegexSection() {
            String md = """
                    ## Config
                    port=8080
                    host=localhost
                    ## Other
                    other stuff
                    """;
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("## Config"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "\\d+"))
                    .value("port=XXXX\nhost=localhost\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).contains("## Config\n");
            assertThat(output).contains("port=XXXX");
            assertThat(output).contains("host=localhost");
            assertThat(output).contains("## Other\nother stuff");
        }

        @Test
        @DisplayName("ReplaceIfMatch applies to ALL matching sections")
        void replaceIfMatchRegexAllSections() {
            String md = """
                    ## Data
                    value=111
                    ## Other
                    untouched
                    ## Data
                    value=222
                    """;
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("## Data"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "\\d+"))
                    .value("value=0\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("111");
            assertThat(output).doesNotContain("222");
            assertThat(output).contains("value=0");
            assertThat(output).contains("## Other\nuntouched");
        }

        // ── RemoveIfMatch (regex) ────────────────────────────────────

        @Test
        @DisplayName("RemoveIfMatch with REGEX matcher removes matching section")
        void removeIfMatchRegexSection() {
            String md = """
                    ## Log
                    INFO: started
                    DEBUG: details
                    INFO: running
                    ## End
                    done
                    """;
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new MarkdownPath("## Log"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "DEBUG:.*"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("## Log");
            assertThat(output).doesNotContain("DEBUG");
            assertThat(output).contains("## End");
        }

        // ── ReplaceIfMatch ───────────────────────────────────────────

        @Test
        @DisplayName("ReplaceIfMatch replaces section when matcher matches content")
        void replaceIfMatchTrue() {
            String md = """
                    ## Status
                    error: something failed
                    ## Info
                    all good
                    """;
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("## Status"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "error"))
                    .value("CLEARED\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).contains("## Status\nCLEARED\n");
            assertThat(output).doesNotContain("something failed");
        }

        @Test
        @DisplayName("ReplaceIfMatch is no-op when matcher does not match")
        void replaceIfMatchFalse() {
            String md = """
                    ## Status
                    all good
                    ## Info
                    info content
                    """;
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("## Status"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "error"))
                    .value("CLEARED\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("all good");
        }

        @Test
        @DisplayName("ReplaceIfMatch with REGEX matcher")
        void replaceIfMatchRegex() {
            String md = """
                    ## Output
                    result: 42
                    ## Other
                    other content
                    """;
            var instruction = Instruction.ReplaceIfMatch.builder()
                    .targetPath(new MarkdownPath("## Output"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.REGEX, "result:\\s+\\d+"))
                    .value("redacted\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("## Output\nredacted\n");
            assertThat(result.r().get()).doesNotContain("result: 42");
        }

        // ── RemoveIfMatch ────────────────────────────────────────────

        @Test
        @DisplayName("RemoveIfMatch removes section when matcher matches")
        void removeIfMatchTrue() {
            String md = """
                    ## Keep
                    safe content
                    ## Secret
                    password: abc123
                    ## Also Keep
                    public info
                    """;
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new MarkdownPath("## Secret"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "password"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("Secret");
            assertThat(output).doesNotContain("password");
            assertThat(output).contains("## Keep\nsafe content");
            assertThat(output).contains("## Also Keep\npublic info");
        }

        @Test
        @DisplayName("RemoveIfMatch is no-op when matcher does not match")
        void removeIfMatchFalse() {
            String md = """
                    ## Section
                    normal content
                    """;
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new MarkdownPath("## Section"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "danger"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).contains("## Section\nnormal content");
        }

        @Test
        @DisplayName("RemoveIfMatch applies to ALL matching sections selectively")
        void removeIfMatchAllSelective() {
            String md = """
                    ## Item
                    safe content
                    ## Item
                    DANGER content
                    ## Item
                    also safe
                    """;
            var instruction = Instruction.RemoveIfMatch.builder()
                    .targetPath(new MarkdownPath("## Item"))
                    .matcher(new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "DANGER"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).doesNotContain("DANGER");
            assertThat(output).contains("safe content");
            assertThat(output).contains("also safe");
        }

        // ── Invalid path ─────────────────────────────────────────────

        @Test
        @DisplayName("Invalid markdown path returns error Result")
        void invalidPath() {
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("not a heading"))
                    .order(0)
                    .build();

            var result = interpreter.apply("# Title\ncontent", List.of(instruction));

            assertThat(result.isErr()).isTrue();
            assertThat(result.e().get().getMessage()).contains("Invalid markdown path");
        }

        // ── Section not found is a no-op ─────────────────────────────

        @Test
        @DisplayName("Non-existent heading is a no-op")
        void nonExistentHeading() {
            String md = """
                    # Title
                    content
                    """;
            var instruction = Instruction.Remove.builder()
                    .targetPath(new MarkdownPath("## Missing"))
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            assertThat(result.r().get()).isEqualTo(md);
        }

        // ── Section boundary ─────────────────────────────────────────

        @Test
        @DisplayName("Section ends at next heading of same or higher level")
        void sectionBoundary() {
            String md = """
                    ## Section
                    section content
                    ### Sub Section
                    sub content
                    ## Next Section
                    next content
                    """;
            var instruction = Instruction.Replace.builder()
                    .targetPath(new MarkdownPath("## Section"))
                    .value("replaced only section\n")
                    .order(0)
                    .build();

            var result = interpreter.apply(md, List.of(instruction));

            assertThat(result.isOk()).isTrue();
            String output = result.r().get();
            assertThat(output).contains("## Section\nreplaced only section\n");
            assertThat(output).doesNotContain("section content");
            assertThat(output).doesNotContain("### Sub Section");
            assertThat(output).contains("## Next Section\nnext content");
        }
    }
}
