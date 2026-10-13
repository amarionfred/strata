package dev.omalauncher.companion;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class OmarchyIntegrationTest {
    private final Path assets = Paths.get("src/main/assets/omarchy");
    private final Path palettes = Paths.get("../../app/src/main/assets/themes");
    private final OmarchyConfigs configs = new OmarchyConfigs(path -> read(assets.resolve(path)));
    private OmarchyPalette palette(String name) throws Exception { return new OmarchyPalette(name, read(palettes.resolve(name + ".toml"))); }
    private static String read(Path path) throws java.io.IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static void put(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
    private static void cleanup(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(item);
        }
    }
    @Test public void everyBundledThemeRendersCompleteNativeConfigs() throws Exception {
        List<Path> files;
        try (var paths = Files.list(palettes)) { files = paths.filter(p -> p.toString().endsWith(".toml")).toList(); }
        assertEquals(22, files.size());
        for (Path path : files) {
            String filename = path.getFileName().toString();
            OmarchyPalette theme = new OmarchyPalette(filename.substring(0, filename.length() - 5), read(path));
            Map<String, String> owned = configs.owned(theme);
            assertFalse(owned.get(OmarchyConfigs.CURRENT + "theme/btop.theme").contains("{{"));
            assertTrue(owned.get(".termux/colors.properties").contains("background=" + theme.colors.get("background")));
            for (int i = 0; i < 16; i++) assertTrue(owned.get(".termux/colors.properties").contains("color" + i + "="));
            assertTrue(configs.install(theme, Commands.HOME).getBytes(StandardCharsets.UTF_8).length < 100000);
        }
    }
    @Test public void installsSwitchesAndBacksUpWithoutReplacingUserAppConfigs() throws Exception {
        Path directory = Files.createTempDirectory("oma 'home-");
        try {
            put(directory.resolve(".termux/colors.properties"), "# user colors\nbackground=#000000\n");
            put(directory.resolve(".config/starship.toml"), "# user prompt\n");
            put(directory.resolve(".config/tmux/tmux.conf"), "# user tmux\n");
            put(directory.resolve(".bashrc"), "# user shell\n");
            OmarchyPalette tokyo = palette("tokyo-night"), nord = palette("nord");
            ProcessRunner.Result first = ProcessRunner.run(10000, "/bin/bash", "--noprofile", "--norc", "-c", configs.install(tokyo, directory.toString()));
            assertEquals(first.output, 0, first.exit);
            assertEquals(tokyo.key(), OmarchyConfigs.acknowledged("omarchy-apply:" + tokyo.key(), first.output, first.exit, "", false));
            assertEquals("# user prompt\n", read(directory.resolve(".config/starship.toml")));
            assertEquals("# user tmux\n", read(directory.resolve(".config/tmux/tmux.conf")));
            assertEquals("# user shell\n", read(directory.resolve(".bashrc")));
            assertTrue(read(directory.resolve(".config/btop/btop.conf")).contains("color_theme = \"current\""));
            Path backup;
            try (var paths = Files.list(directory.resolve(".local/state/oma"))) { backup = paths.findFirst().orElseThrow(); }
            assertEquals("# user colors\nbackground=#000000\n", read(backup.resolve(".termux/colors.properties")));
            ProcessRunner.Result switched = ProcessRunner.run(10000, "/bin/bash", "--noprofile", "--norc", "-c", configs.install(nord, directory.toString()));
            assertEquals(switched.output, 0, switched.exit);
            assertEquals("nord\n", read(directory.resolve(OmarchyConfigs.CURRENT + "theme.name")));
            assertEquals(OmarchyConfigs.termuxColors(nord), read(directory.resolve(".termux/colors.properties")));
            assertEquals(configs.owned(nord).get(OmarchyConfigs.CURRENT + "theme/btop.theme"), read(directory.resolve(".config/btop/themes/current.theme")));
        } finally { cleanup(directory); }
    }
    @Test public void aWriteFailureCannotAcknowledgeAnInstall() throws Exception {
        Path directory = Files.createTempDirectory("oma-failed-install-");
        try {
            Files.createDirectories(directory.resolve(".termux/colors.properties"));
            OmarchyPalette theme = palette("tokyo-night");
            ProcessRunner.Result result = ProcessRunner.run(10000, "/bin/bash", "-c", configs.install(theme, directory.toString()));
            assertNotEquals(0, result.exit);
            assertFalse(result.output.contains("OMA_THEME_APPLIED="));
            assertFalse(Files.exists(directory.resolve(OmarchyConfigs.CURRENT + "oma-theme.key")));
        } finally { cleanup(directory); }
    }
    @Test public void interactiveShellLoadsUserStartupAndSessionOverrides() throws Exception {
        Path directory = Files.createTempDirectory("oma-interactive-");
        try {
            put(directory.resolve(".bashrc"), "export OMA_STARTUP_TEST=loaded\nexport EDITOR=from_bashrc\n");
            put(directory.resolve(Commands.CONFIG), "export EDITOR=from_session\n");
            ProcessRunner.Result install = ProcessRunner.run(10000, "/bin/bash", "-c", configs.install(palette("tokyo-night"), directory.toString()));
            assertEquals(install.output, 0, install.exit);
            ProcessRunner.Result shell = ProcessRunner.run(10000, "/usr/bin/env", "HOME=" + directory,
                    "PREFIX=/usr", "PATH=/usr/bin:/bin", "TERM=xterm-256color", "/bin/bash", "--noprofile",
                    "--rcfile", directory.resolve(OmarchyConfigs.INTERACTIVE).toString(), "-i", "-c",
                    "[ \"$OMA_STARTUP_TEST\" = loaded ] && [ \"$EDITOR\" = from_session ] && alias g && "
                            + "[ \"$HISTSIZE\" = 32768 ] && [ \"$INPUTRC\" = \"$HOME/.local/share/omarchy/default/bash/inputrc\" ]");
            assertEquals(shell.output, 0, shell.exit);
            assertTrue(shell.output.contains("alias g='git'"));
        } finally { cleanup(directory); }
    }
    @Test public void importedColorTextCannotRunCommands() throws Exception {
        Path directory = Files.createTempDirectory("oma-import-");
        try {
            Path marker = directory.resolve("unexpected");
            String colors = palette("tokyo-night").source + "\n$(touch " + Commands.quote(marker.toString()) + ")\n";
            OmarchyPalette imported = new OmarchyPalette("imported-test", colors);
            ProcessRunner.Result result = ProcessRunner.run(10000, "/bin/bash", "-c", configs.install(imported, directory.toString()));
            assertEquals(result.output, 0, result.exit);
            assertFalse(Files.exists(marker));
            assertEquals(colors, read(directory.resolve(OmarchyConfigs.CURRENT + "theme/colors.toml")));
        } finally { cleanup(directory); }
    }
    @Test public void malformedColorsAndPathsAreRejected() throws Exception {
        String valid = palette("tokyo-night").source;
        assertThrows(IllegalArgumentException.class, () -> new OmarchyPalette("../escape", valid));
        assertThrows(IllegalArgumentException.class, () -> new OmarchyPalette("test", valid.replace("#1a1b26", "#1a1b26; echo injected")));
        assertThrows(IllegalArgumentException.class, () -> new OmarchyPalette("test", "mode = \"dark\"\n"));
        assertThrows(IllegalArgumentException.class, () -> new OmarchyPalette("test", valid + "\nforeground = \"$(id)\"\n"));
    }
    @Test public void shellFilesAreValidAndDoNotInvokeDesktopHelpers() throws Exception {
        OmarchyPalette theme = palette("tokyo-night");
        Map<String, String> files = configs.owned(theme);
        for (String path : new String[]{OmarchyConfigs.BASH, OmarchyConfigs.INTERACTIVE,
                ".local/share/omarchy/default/bash/shell", ".local/share/omarchy/default/bash/aliases"}) {
            ProcessRunner.Result parsed = ProcessRunner.run(2000, "/bin/bash", "-n", "-c", files.get(path));
            assertEquals(parsed.output, 0, parsed.exit);
        }
        String aliases = files.get(".local/share/omarchy/default/bash/aliases");
        assertFalse(aliases.contains("--approve-for-me"));
        assertFalse(aliases.contains("alias a="));
        assertTrue(aliases.contains("alias g='git'"));
        assertTrue(aliases.contains("termux-open"));
        assertFalse(configs.seeded().get(".config/tmux/tmux.conf").contains("omarchy-menu-tmux"));
    }
    @Test public void successRequiresTheMatchingCompleteAcknowledgement() throws Exception {
        OmarchyPalette theme = palette("tokyo-night");
        String kind = "omarchy-apply:" + theme.key(), output = "OMA_THEME_APPLIED=" + theme.key() + "\n";
        assertEquals(theme.key(), OmarchyConfigs.acknowledged(kind, output, 0, "", false));
        assertNull(OmarchyConfigs.acknowledged(kind, output, 1, "", false));
        assertNull(OmarchyConfigs.acknowledged(kind, output, 0, "runtime failure", false));
        assertNull(OmarchyConfigs.acknowledged(kind, output, 0, "", true));
        assertNull(OmarchyConfigs.acknowledged("terminal", output, 0, "", false));
        assertNull(OmarchyConfigs.acknowledged(kind, "", 0, "", false));
        assertNull(OmarchyConfigs.acknowledged("omarchy-apply:" + palette("nord").key(), output, 0, "", false));
    }
}
