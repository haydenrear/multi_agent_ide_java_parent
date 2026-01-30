package com.hayden.utilitymodule.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsPathResolverTest {

    @TempDir
    Path tmp;

    private Path touch(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        return Files.writeString(p, "hi");
    }

    @Test
    void resolvesRelativeUnderRoot() throws IOException {
        // root: /tmp/.../okay
        Path root = Files.createDirectories(tmp.resolve("Users/first/okay"));
        Path target = root.resolve("one/two/file.txt");
        touch(target);

        // ask for: "okay/one/two/file.txt" (relative form from user's example)
        Path query = Paths.get("okay/one/two/file.txt");

        var res = FileUtils.getPathFor(query, tmp, Map.of());
        assertTrue(res.isOk(), () -> "Expected resolution OK, got error: " + (res.unwrapError() == null ? "" : res.unwrapError().getMessage()));
        assertEquals(target.toRealPath(), res.unwrap().toRealPath());
        Path queryTwo = Paths.get("first/okay/one/two/file.txt");
        var resAgain = FileUtils.getPathFor(queryTwo, tmp, Map.of());
        assertTrue(res.isOk(), () -> "Expected resolution OK, got error: " + (resAgain.unwrapError() == null ? "" : resAgain.unwrapError().getMessage()));
        assertEquals(target.toRealPath(), res.unwrap().toRealPath());
        Path queryThree = Paths.get("/first/okay/one/two/file.txt");
        var resAgainTwo = FileUtils.getPathFor(queryThree, tmp, Map.of());
        assertTrue(res.isOk(), () -> "Expected resolution OK, got error: " + (resAgainTwo.unwrapError() == null ? "" : resAgainTwo.unwrapError().getMessage()));
        assertEquals(target.toRealPath(), res.unwrap().toRealPath());

        var parentRoot = tmp.getParent();
        var parentName = parentRoot.getFileName().toString();
        var parent = Paths.get(parentName).resolve(tmp.subpath(tmp.getNameCount() - 1, tmp.getNameCount()));
        Path queryFour = parent.resolve("Okay.java");
        var resAgainThree = FileUtils.getPathFor(queryFour, tmp, Map.of()).unwrap();
        assertEquals(resAgainThree, tmp.resolve("Okay.java"));
        Path queryFive = tmp.resolve("Okay.java");
        var resAgainFour = FileUtils.getPathFor(queryFive, tmp, Map.of()).unwrap();
        assertEquals(resAgainFour, tmp.resolve("Okay.java"));

        parentRoot = tmp.getParent().getParent();
        parentName = parentRoot.getFileName().toString();
        parent = Paths.get(parentName).resolve(tmp.getParent()).resolve(tmp.subpath(tmp.getNameCount() - 1, tmp.getNameCount()));
        queryFour = parent.resolve("Okay.java");
        resAgainThree = FileUtils.getPathFor(queryFour, tmp, Map.of()).unwrap();
        assertEquals(resAgainThree, tmp.resolve("Okay.java"));
    }

    @Test
    void rebasesAbsoluteWithDifferentPrefix() throws IOException {
        // root: /tmp/.../Users/first/okay
        Path root = Files.createDirectories(tmp.resolve("Users/first/okay"));
        Path target = root.resolve("one/two/file.txt");
        touch(target);

        // ask for: "/okay/one/two/file.txt"
        // NOTE: use system-independent absolute by prefixing root separator
        Path absDifferentRoot = Paths.get("/okay/one/two/file.txt"); // On Windows this will map to \okay\...
        var res = FileUtils.getPathFor(absDifferentRoot, tmp.resolve("Users/first"), Map.of());
        assertTrue(res.isOk());
        assertEquals(target.toRealPath(), res.unwrap().toRealPath());
    }

    @Test
    void suffixMatchFindsFileUnderRoot() throws IOException {
        Path root = Files.createDirectories(tmp.resolve("Users/first/okay"));
        Path target = root.resolve("one/two/three/file.txt");
        touch(target);

        // query has an unrelated prefix but correct tail
        Path noisy = Paths.get("/unrelated/prefix/one/two/three/file.txt");

        var res = FileUtils.getPathFor(noisy, root, Map.of());
        assertTrue(res.isOk());
        assertEquals(target.toRealPath(), res.unwrap().toRealPath());
    }

    @Test
    void replacementMapRewritesPath() throws IOException {
        Path root = Files.createDirectories(tmp.resolve("base"));
        Path target = root.resolve("one/two/file.txt");
        touch(target);

        // Start with a path containing a placeholder segment
        Path withPlaceholder = Paths.get("XROOT/one/two/file.txt");
        Map<String, String> replace = Map.of("XROOT", root.toString());

        var res = FileUtils.getPathFor(withPlaceholder, root, replace);
        assertTrue(res.isOk());
        assertEquals(target.toRealPath(), res.unwrap().toRealPath());
    }

    @Test
    void getClosestPathForSearch_prefersDirectUnderAnyRoot() throws IOException {
        Path rootA = Files.createDirectories(tmp.resolve("rootA"));
        Path rootB = Files.createDirectories(tmp.resolve("rootB/sub"));
        Path targetB = rootB.resolve("ok/thing/file.txt");
        touch(targetB);

        // Ask with a relative path that should match under rootB
        Path query = Paths.get("ok/thing/file.txt");

        Path found = FileUtils.getClosestPathForSearch(query, List.of(rootA, rootB));
        assertNotNull(found);
        assertEquals(targetB.toRealPath(), found.toRealPath());
    }

    @Test
    void getClosestPathForSearch_rebasesAbsolute() throws IOException {
        Path root = Files.createDirectories(tmp.resolve("project/root"));
        Path target = root.resolve("src/main/java/App.java");
        touch(target);

        // Absolute with different prefix
        Path abs = Paths.get("/src/main/java/App.java");
        Path found = FileUtils.getClosestPathForSearch(abs, List.of(root, tmp));
        assertNotNull(found);
        assertEquals(target.toRealPath(), found.toRealPath());
    }

    @Test
    void rebasesOntoWhenNotFound() {
        var res = FileUtils.getPathFor(
                Paths.get("exist.txt"),
                tmp,
                Map.of()
        );
        assertTrue(res.isOk());
        assertTrue(res.unwrap().equals(tmp.resolve("exist.txt")));
    }
}
