package dev.omalauncher.companion;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.*;

/** Execute generated shell arguments and process edge cases on the host, without Android mocks. */
public class NativeBoundaryTest {
    @Test public void shellArgumentsRoundTripWithoutExecutingMetacharacters() throws Exception {
        Path directory = Files.createTempDirectory("oma-quote-");
        Path marker = directory.resolve("unexpected");
        String hostile = "'\"; touch " + marker + "; $(touch " + marker + ") `touch " + marker + "`\n日本語 \\ *";
        ProcessRunner.Result result = ProcessRunner.run(2000, "/bin/bash", "--noprofile", "--norc", "-c", "printf %s " + Commands.quote(hostile));
        assertEquals(0, result.exit);
        assertEquals(hostile, result.output);
        assertFalse(Files.exists(marker));
        Files.delete(directory);
    }
    @Test public void packageArgumentsRejectOptionsAndShellInjection() {
        for (String name : new String[]{"-y", "git;id", "$(id)", "git nano", "../pkg", "git\nwhoami", "", "a:b"}) {
            assertThrows(IllegalArgumentException.class, () -> Commands.install(name));
        }
        assertTrue(Commands.install("libc++").endsWith("'libc++'"));
        assertTrue(Commands.install("python").endsWith("'python'"));
    }
    @Test public void argumentsRejectNulAndOversizedConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> Commands.quote("a\0b"));
        assertThrows(IllegalArgumentException.class, () -> Commands.terminal(" "));
        assertThrows(IllegalArgumentException.class, () -> Commands.saveConfig("é".repeat(8001)));
    }
    @Test public void configContentsAreNotExecutedDuringSave() throws Exception {
        Path directory = Files.createTempDirectory("oma-config-test-");
        try {
            Path marker = directory.resolve("unexpected");
            String contents = "# test\ntouch " + Commands.quote(marker.toString()) + "\n";
            ProcessRunner.Result saved = ProcessRunner.run(2000, "/bin/bash", "-c", Commands.saveConfigAt(contents, directory.toString()));
            assertEquals(0, saved.exit);
            assertEquals(contents, read(directory.resolve(Commands.CONFIG)));
            assertFalse(Files.exists(marker));
        } finally { deleteFixture(directory); }
    }
    @Test public void configurationBackupAndSyntaxFailurePreserveData() throws Exception {
        Path directory = Files.createTempDirectory("oma-config-state-");
        try {
            String first = "export EDITOR=nano\n", second = "export EDITOR=vi\n";
            assertEquals(0, ProcessRunner.run(2000, "/bin/bash", "-c", Commands.saveConfigAt(first, directory.toString())).exit);
            assertEquals(0, ProcessRunner.run(2000, "/bin/bash", "-c", Commands.saveConfigAt(second, directory.toString())).exit);
            assertEquals(first, read(directory.resolve(Commands.CONFIG + ".previous")));
            assertNotEquals(0, ProcessRunner.run(2000, "/bin/bash", "-c", Commands.saveConfigAt("if (", directory.toString())).exit);
            assertEquals(second, read(directory.resolve(Commands.CONFIG)));
            assertEquals(first, read(directory.resolve(Commands.CONFIG + ".previous")));
            try (var paths = Files.list(directory.resolve(".config/oma"))) { assertEquals(2, paths.count()); }
        } finally { deleteFixture(directory); }
    }
    private static void deleteFixture(Path path) throws Exception {
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(item);
        }
    }
    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    @Test public void exitStatusAndStderrArePreserved() throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(2000, "/bin/bash", "-c", "printf failure >&2; exit 23");
        assertEquals(23, result.exit);
        assertEquals("failure", result.output);
    }
    @Test public void outputIsDrainedAndBounded() throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(4000, "/bin/bash", "-c", "head -c 200000 /dev/zero | tr '\\0' x");
        assertEquals(0, result.exit);
        assertEquals(ProcessRunner.MAX_OUTPUT + "\n[output truncated]".length(), result.output.length());
        assertTrue(result.output.endsWith("[output truncated]"));
    }
    @Test public void blockedProcessTimesOut() {
        long start = System.nanoTime();
        assertThrows(TimeoutException.class, () -> ProcessRunner.run(100, "/bin/sleep", "10"));
        assertTrue("Deadline should not wait for the command's natural exit", System.nanoTime() - start < 3_000_000_000L);
    }
}
