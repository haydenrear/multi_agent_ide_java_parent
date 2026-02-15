package com.hayden.multiagentide.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class PromptFileService {

    private static final List<String> EXTENSIONS = List.of(".jinja", ".txt", ".prompt", ".md");

    private final Path workspaceRoot;
    private final Path defaultPromptRoot;

    public PromptFileService() {
        this(resolveWorkspaceRoot(), resolveDefaultPromptRoot(resolveWorkspaceRoot()));
    }

    public PromptFileService(Path workspaceRoot, Path defaultPromptRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.defaultPromptRoot = defaultPromptRoot.toAbsolutePath().normalize();
    }

    public List<PromptFileReference> discover() {
        if (!Files.exists(defaultPromptRoot)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(defaultPromptRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isPromptFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(this::toReference)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public PromptChangeResult addPrompt(AddPromptRequest request) {
        try {
            Path target = resolvePath(request.path());
            validatePath(target);
            Files.createDirectories(target.getParent());
            Files.writeString(target, request.content(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return PromptChangeResult.success(request.promptKey(), "ADD", "Prompt file added at " + target);
        } catch (Exception e) {
            return PromptChangeResult.failure(request.promptKey(), "ADD", e.getMessage());
        }
    }

    public PromptChangeResult updatePrompt(String promptKey, String content) {
        try {
            Path target = resolveByPromptKey(promptKey);
            validatePath(target);
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return PromptChangeResult.success(promptKey, "UPDATE", "Prompt updated at " + target);
        } catch (Exception e) {
            return PromptChangeResult.failure(promptKey, "UPDATE", e.getMessage());
        }
    }

    private Path resolveByPromptKey(String promptKey) {
        if (promptKey == null || promptKey.isBlank()) {
            throw new IllegalArgumentException("promptKey is required");
        }

        List<PromptFileReference> refs = discover();
        for (PromptFileReference ref : refs) {
            if (ref.promptKey().equals(promptKey)) {
                return Path.of(ref.path()).normalize();
            }
            String fileName = Path.of(ref.path()).getFileName().toString();
            String stem = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            if (stem.equals(promptKey)) {
                return Path.of(ref.path()).normalize();
            }
        }

        // Allow creating by key inside workflow directory when no exact file exists.
        return defaultPromptRoot.resolve(promptKey + ".jinja").normalize();
    }

    private Path resolvePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return workspaceRoot.resolve(path).normalize();
    }

    private void validatePath(Path path) {
        if (!path.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path must stay in workspace: " + path);
        }
    }

    private boolean isPromptFile(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private PromptFileReference toReference(Path path) {
        Instant lastModified = null;
        try {
            lastModified = Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
        }
        return new PromptFileReference(
                promptKeyFor(path),
                path.toString(),
                "Prompt template",
                Files.exists(path),
                lastModified
        );
    }

    private String promptKeyFor(Path path) {
        Path relative;
        try {
            relative = defaultPromptRoot.relativize(path);
        } catch (Exception ignored) {
            relative = path.getFileName();
        }
        String normalized = relative.toString().replace('\\', '/');
        for (String extension : EXTENSIONS) {
            if (normalized.endsWith(extension)) {
                return normalized.substring(0, normalized.length() - extension.length()).replace('/', '.');
            }
        }
        return normalized.replace('/', '.');
    }

    private static Path resolveWorkspaceRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static Path resolveDefaultPromptRoot(Path workspaceRoot) {
        return workspaceRoot.resolve("multi_agent_ide_java_parent")
                .resolve("multi_agent_ide")
                .resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("prompts")
                .resolve("workflow")
                .normalize();
    }

    public record PromptFileReference(
            String promptKey,
            String path,
            String description,
            boolean exists,
            Instant lastModifiedAt
    ) {
    }

    public record AddPromptRequest(String promptKey, String path, String content) {
    }

    public record PromptChangeResult(
            String changeId,
            String promptKey,
            String operation,
            String result,
            String message,
            Instant appliedAt
    ) {
        public static PromptChangeResult success(String promptKey, String operation, String message) {
            return new PromptChangeResult(UUID.randomUUID().toString(), promptKey, operation, "SUCCESS", message, Instant.now());
        }

        public static PromptChangeResult failure(String promptKey, String operation, String message) {
            return new PromptChangeResult(UUID.randomUUID().toString(), promptKey, operation, "FAILED", message, Instant.now());
        }
    }
}
