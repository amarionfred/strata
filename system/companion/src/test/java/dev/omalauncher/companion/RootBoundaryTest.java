package dev.omalauncher.companion;

import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;

public class RootBoundaryTest {
    @Test public void uidCheckPrecedesAnyPayloadExecution() throws Exception {
        Path directory = Files.createTempDirectory("strata-uid-");
        Path marker = directory.resolve("payload");
        try {
            boolean root = ProcessRunner.run(2000, "/usr/bin/id", "-u").output.trim().equals("0");
            String payload = "printf payload > " + Commands.quote(marker.toString()) + "; exit 29";
            ProcessRunner.Result result = ProcessRunner.run(2000, "/bin/sh", "-c", RootCommands.guard(payload, "/usr/bin/id", "/bin/sh"));
            assertEquals(root ? 29 : 126, result.exit);
            assertEquals(root, Files.exists(marker));
            assertEquals(root, result.output.startsWith(RootCommands.VERIFIED + "\n"));
        } finally { Files.deleteIfExists(marker); Files.delete(directory); }
    }

    @Test public void missingUidBinaryNeverExecutesPayload() throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(2000, "/bin/sh", "-c",
                RootCommands.guard("printf SHOULD_NOT_EXECUTE", "/no-such-strata-id-binary", "/bin/sh"));
        assertEquals(126, result.exit);
        assertFalse(result.output.contains("SHOULD_NOT_EXECUTE"));
        assertFalse(result.output.contains(RootCommands.VERIFIED));
    }

    @Test public void packageServiceAndControlInputsRejectShellInjection() {
        for (String name : new String[]{"", "-f", "../service", "a/b", "x;id", "$(id)", "two words", "a\nb"}) {
            assertThrows(IllegalArgumentException.class, () -> RootCommands.install(name));
            assertThrows(IllegalArgumentException.class, () -> RootCommands.serviceName(name));
        }
        assertThrows(IllegalArgumentException.class, () -> RootCommands.control("start;id"));
        assertThrows(IllegalArgumentException.class, () -> RootCommands.command("a\0b"));
        assertThrows(IllegalArgumentException.class, () -> RootCommands.command(" "));
        assertThrows(IllegalArgumentException.class, () -> RootCommands.command("x".repeat(16001)));
        assertTrue(RootCommands.install("libstdc++").endsWith("'--' 'libstdc++'"));
        assertThrows(IllegalArgumentException.class, () -> RootCommands.files("relative", false));
    }

    @Test public void serviceSavePreservesScriptWithoutExecutingIt() throws Exception {
        Path directory = Files.createTempDirectory("strata-services-");
        try {
            Path marker = directory.resolve("unexpected");
            String script = "printf '%s\\n' \"$(touch " + Commands.quote(marker.toString()) + ")\"\nexit 7";
            ProcessRunner.Result result = save(directory, script);
            assertEquals(result.output, 0, result.exit);
            assertFalse(Files.exists(marker));
            assertEquals("#!/bin/sh\n" + script + "\n", read(directory.resolve("test-service")));
            assertTrue(Files.isExecutable(directory.resolve("test-service")));
        } finally { deleteFixture(directory); }
    }

    @Test public void serviceSyntaxFailurePreservesCurrentAndHiddenBackup() throws Exception {
        Path directory = Files.createTempDirectory("strata-service-state-");
        try {
            assertEquals(0, save(directory, "exec sleep 3").exit);
            String previous = read(directory.resolve("test-service"));
            assertEquals(0, save(directory, "exec sleep 4").exit);
            String current = read(directory.resolve("test-service"));
            assertNotEquals(0, save(directory, "if (").exit);
            assertEquals(previous, read(directory.resolve(".test-service.previous")));
            assertEquals(current, read(directory.resolve("test-service")));
            try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
        } finally { deleteFixture(directory); }
    }

    private static ProcessRunner.Result save(Path directory, String script) throws Exception {
        return ProcessRunner.run(2000, "/bin/sh", "-c", RootCommands.saveServiceAt("test-service", script, directory.toString()));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void deleteFixture(Path directory) throws Exception {
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
