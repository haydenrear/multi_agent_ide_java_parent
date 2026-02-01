package com.hayden.acp_cdc_ai.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxArgUtilsTest {

    @Nested
    @DisplayName("hasFlag")
    class HasFlagTests {

        @Test
        @DisplayName("should return true when flag exists")
        void shouldReturnTrueWhenFlagExists() {
            List<String> args = Arrays.asList("--verbose", "--debug", "value");
            
            assertThat(SandboxArgUtils.hasFlag(args, "--verbose")).isTrue();
            assertThat(SandboxArgUtils.hasFlag(args, "--debug")).isTrue();
        }

        @Test
        @DisplayName("should return false when flag does not exist")
        void shouldReturnFalseWhenFlagDoesNotExist() {
            List<String> args = Arrays.asList("--verbose", "--debug");
            
            assertThat(SandboxArgUtils.hasFlag(args, "--quiet")).isFalse();
        }

        @Test
        @DisplayName("should return true when any of multiple flags exists")
        void shouldReturnTrueWhenAnyFlagExists() {
            List<String> args = Arrays.asList("--cd", "/path/to/dir");
            
            assertThat(SandboxArgUtils.hasFlag(args, "--cd", "-C")).isTrue();
            assertThat(SandboxArgUtils.hasFlag(args, "-C", "--cd")).isTrue();
        }

        @Test
        @DisplayName("should return false for null or empty args")
        void shouldReturnFalseForNullOrEmptyArgs() {
            assertThat(SandboxArgUtils.hasFlag(null, "--verbose")).isFalse();
            assertThat(SandboxArgUtils.hasFlag(Collections.emptyList(), "--verbose")).isFalse();
        }

        @Test
        @DisplayName("should handle short flags")
        void shouldHandleShortFlags() {
            List<String> args = Arrays.asList("-v", "-d", "value");
            
            assertThat(SandboxArgUtils.hasFlag(args, "-v")).isTrue();
            assertThat(SandboxArgUtils.hasFlag(args, "-x")).isFalse();
        }
    }

    @Nested
    @DisplayName("hasFlagValuePair")
    class HasFlagValuePairTests {

        @Test
        @DisplayName("should return true when flag is immediately followed by value")
        void shouldReturnTrueWhenFlagFollowedByValue() {
            List<String> args = Arrays.asList("--add-dir", "/path/a", "--add-dir", "/path/b");
            
            assertThat(SandboxArgUtils.hasFlagValuePair(args, "/path/a", "--add-dir")).isTrue();
            assertThat(SandboxArgUtils.hasFlagValuePair(args, "/path/b", "--add-dir")).isTrue();
        }

        @Test
        @DisplayName("should return false when value exists but not after the flag")
        void shouldReturnFalseWhenValueNotAfterFlag() {
            List<String> args = Arrays.asList("--other", "/path/a", "--add-dir", "/path/b");
            
            // /path/a exists but is after --other, not --add-dir
            assertThat(SandboxArgUtils.hasFlagValuePair(args, "/path/a", "--add-dir")).isFalse();
        }

        @Test
        @DisplayName("should return false when flag exists but with different value")
        void shouldReturnFalseWhenFlagHasDifferentValue() {
            List<String> args = Arrays.asList("--add-dir", "/path/a");
            
            assertThat(SandboxArgUtils.hasFlagValuePair(args, "/path/b", "--add-dir")).isFalse();
        }

        @Test
        @DisplayName("should handle multiple flag variants")
        void shouldHandleMultipleFlagVariants() {
            List<String> args = Arrays.asList("-C", "/path/to/dir");
            
            assertThat(SandboxArgUtils.hasFlagValuePair(args, "/path/to/dir", "--cd", "-C")).isTrue();
        }

        @Test
        @DisplayName("should return false for null or insufficient args")
        void shouldReturnFalseForNullOrInsufficientArgs() {
            assertThat(SandboxArgUtils.hasFlagValuePair(null, "/path", "--add-dir")).isFalse();
            assertThat(SandboxArgUtils.hasFlagValuePair(Collections.emptyList(), "/path", "--add-dir")).isFalse();
            assertThat(SandboxArgUtils.hasFlagValuePair(List.of("--add-dir"), "/path", "--add-dir")).isFalse();
        }

        @Test
        @DisplayName("should return false when value is null")
        void shouldReturnFalseWhenValueIsNull() {
            List<String> args = Arrays.asList("--add-dir", "/path/a");
            
            assertThat(SandboxArgUtils.hasFlagValuePair(args, null, "--add-dir")).isFalse();
        }

        @Test
        @DisplayName("should not match flag at end of list")
        void shouldNotMatchFlagAtEndOfList() {
            List<String> args = Arrays.asList("/path/a", "--add-dir");
            
            assertThat(SandboxArgUtils.hasFlagValuePair(args, "/path/a", "--add-dir")).isFalse();
        }
    }

    @Nested
    @DisplayName("getFlagValue")
    class GetFlagValueTests {

        @Test
        @DisplayName("should return value following flag")
        void shouldReturnValueFollowingFlag() {
            List<String> args = Arrays.asList("--sandbox", "workspace-write", "--cd", "/path");
            
            assertThat(SandboxArgUtils.getFlagValue(args, "--sandbox")).isEqualTo("workspace-write");
            assertThat(SandboxArgUtils.getFlagValue(args, "--cd")).isEqualTo("/path");
        }

        @Test
        @DisplayName("should return first matching value when multiple flags specified")
        void shouldReturnFirstMatchingValue() {
            List<String> args = Arrays.asList("-s", "read-only", "--sandbox", "workspace-write");
            
            assertThat(SandboxArgUtils.getFlagValue(args, "-s", "--sandbox")).isEqualTo("read-only");
            assertThat(SandboxArgUtils.getFlagValue(args, "--sandbox", "-s")).isEqualTo("read-only");
        }

        @Test
        @DisplayName("should return null when flag not found")
        void shouldReturnNullWhenFlagNotFound() {
            List<String> args = Arrays.asList("--sandbox", "workspace-write");
            
            assertThat(SandboxArgUtils.getFlagValue(args, "--cd")).isNull();
        }

        @Test
        @DisplayName("should return null for null or insufficient args")
        void shouldReturnNullForNullOrInsufficientArgs() {
            assertThat(SandboxArgUtils.getFlagValue(null, "--sandbox")).isNull();
            assertThat(SandboxArgUtils.getFlagValue(Collections.emptyList(), "--sandbox")).isNull();
            assertThat(SandboxArgUtils.getFlagValue(List.of("--sandbox"), "--sandbox")).isNull();
        }
    }

    @Nested
    @DisplayName("containsValue")
    class ContainsValueTests {

        @Test
        @DisplayName("should return true when value exists anywhere")
        void shouldReturnTrueWhenValueExists() {
            List<String> args = Arrays.asList("--add-dir", "/path/a", "--other", "/path/b");
            
            assertThat(SandboxArgUtils.containsValue(args, "/path/a")).isTrue();
            assertThat(SandboxArgUtils.containsValue(args, "/path/b")).isTrue();
            assertThat(SandboxArgUtils.containsValue(args, "--add-dir")).isTrue();
        }

        @Test
        @DisplayName("should return false when value does not exist")
        void shouldReturnFalseWhenValueDoesNotExist() {
            List<String> args = Arrays.asList("--add-dir", "/path/a");
            
            assertThat(SandboxArgUtils.containsValue(args, "/path/b")).isFalse();
        }

        @Test
        @DisplayName("should return false for null args or value")
        void shouldReturnFalseForNullArgsOrValue() {
            assertThat(SandboxArgUtils.containsValue(null, "/path")).isFalse();
            assertThat(SandboxArgUtils.containsValue(List.of("--flag"), null)).isFalse();
        }
    }
}
