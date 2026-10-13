package dev.omalauncher.companion;

import dev.omalauncher.theme.Templates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native adaptation of GuestThemeConfig. No desktop installer or root commands. */
final class OmarchyConfigs {
    interface Source { String read(String path) throws IOException; }
    static final String REVISION = "8b4eae66da2938ba9559f103b18dbf85cdf28a70";
    static final String CURRENT = ".local/state/omarchy/current/";
    static final String INTERACTIVE = ".config/oma/interactive.bash";
    static final String BASH = ".config/oma/omarchy.bash";
    private final Source source;
    OmarchyConfigs(Source source) { this.source = source; }

    Map<String, String> owned(OmarchyPalette palette) throws IOException {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(".local/share/omarchy/LICENSE", source.read("LICENSE"));
        files.put(".local/share/omarchy/.omalauncher-ref", REVISION + "\n");
        files.put(".local/share/omarchy/themes/" + palette.name + "/colors.toml", palette.source);
        files.put(CURRENT + "theme/colors.toml", palette.source);
        String btop;
        try { btop = source.read("themes/" + palette.name + "/btop.theme"); }
        catch (IOException missingOverride) { btop = Templates.INSTANCE.render(source.read("default/themed/btop.theme.tpl"), palette.colors); }
        if (btop.contains("{{")) throw new IllegalArgumentException("Unresolved btop theme template");
        files.put(CURRENT + "theme/btop.theme", btop);
        files.put(".config/btop/themes/current.theme", btop);
        files.put(".termux/colors.properties", termuxColors(palette));
        files.put(".local/share/omarchy/default/bash/inputrc", source.read("default/bash/inputrc"));
        files.put(".local/share/omarchy/default/bash/shell", source.read("default/bash/shell").replace("/usr/share/", "${PREFIX}/share/"));
        files.put(".local/share/omarchy/default/bash/aliases", portableAliases(source.read("default/bash/aliases")));
        files.put(BASH, "# Oma's native Android adapter for Omarchy " + REVISION + "\n"
                + "export OMARCHY_PATH=\"$HOME/.local/share/omarchy\"\n"
                + "export INPUTRC=\"$OMARCHY_PATH/default/bash/inputrc\"\n"
                + "export EDITOR=\"${EDITOR:-nano}\"\n"
                + "source \"$OMARCHY_PATH/default/bash/shell\"\n"
                + "source \"$OMARCHY_PATH/default/bash/aliases\"\n"
                + "if [[ $- == *i* ]]; then\n"
                + "  bind -f \"$INPUTRC\"\n"
                + "  if command -v starship >/dev/null; then eval \"$(starship init bash)\"; fi\n"
                + "  if command -v zoxide >/dev/null; then eval \"$(zoxide init bash)\"; fi\n"
                + "  if [[ -f \"$PREFIX/share/fzf/key-bindings.bash\" ]]; then source \"$PREFIX/share/fzf/key-bindings.bash\"; fi\n"
                + "fi\n");
        files.put(INTERACTIVE, "# Keeps the user's startup file, then layers Omarchy and their session overrides.\n"
                + "[[ -n ${OMA_INTERACTIVE_ACTIVE:-} ]] && return\nOMA_INTERACTIVE_ACTIVE=1\n"
                + "[[ -r \"$HOME/.bashrc\" ]] && source \"$HOME/.bashrc\"\n"
                + "source \"$HOME/" + BASH + "\"\n"
                + "if [[ -r \"$HOME/" + Commands.CONFIG + "\" ]]; then source \"$HOME/" + Commands.CONFIG + "\"; fi\n");
        // Last writes are the marker/readback. Failed writes cannot return a successful sync marker.
        files.put(CURRENT + "theme.name", palette.name + "\n");
        files.put(CURRENT + "oma-theme.key", palette.key() + "\n");
        return files;
    }
    Map<String, String> seeded() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(".config/starship.toml", source.read("config/starship.toml"));
        files.put(".config/btop/btop.conf", source.read("config/btop/btop.conf"));
        files.put(".config/tmux/tmux.conf", source.read("config/tmux/tmux.conf")
                .replace("omarchy-menu-tmux-keybindings --print | less -R", "tmux list-keys | less -R"));
        return files;
    }
    static String portableAliases(String original) {
        StringBuilder out = new StringBuilder("# Omarchy aliases; unsupported desktop/agent helpers are omitted.\n");
        for (String line : original.split("\n", -1)) {
            if (line.matches("alias (a|c|cx|cy|d|r|h|ic|ix|icx|mup)=.*")) continue;
            out.append(line.replace("xdg-open \"$@\"", "termux-open \"$@\"")).append('\n');
        }
        return out.toString();
    }
    static String termuxColors(OmarchyPalette palette) {
        StringBuilder result = new StringBuilder("# Omarchy: " + palette.name + "\n");
        for (String key : new String[]{"background", "foreground", "cursor"}) result.append(key).append('=').append(palette.colors.get(key)).append('\n');
        for (int i = 0; i < 16; i++) result.append("color").append(i).append('=').append(palette.colors.get("color" + i)).append('\n');
        return result.toString();
    }
    String install(OmarchyPalette palette, String home) throws IOException {
        StringBuilder script = new StringBuilder("set -eu\numask 077\noma_home=" + Commands.quote(home) + "\n"
                + "mkdir -p -- \"$oma_home/.local/state/oma\"\n"
                + "oma_backup=$(mktemp -d \"$oma_home/.local/state/oma/config-backup.XXXXXX\")\n"
                + "oma_tmp=''\ntrap '[ -z \"$oma_tmp\" ] || rm -f -- \"$oma_tmp\"' EXIT\n"
                + "oma_write() {\n"
                + "  local relative=$1 policy=$2 contents=$3 target=\"$oma_home/$1\"\n"
                + "  if [ \"$policy\" = seed ] && { [ -e \"$target\" ] || [ -L \"$target\" ]; }; then printf 'Kept %s\\n' \"$relative\"; return; fi\n"
                + "  if [ -e \"$target\" ] || [ -L \"$target\" ]; then\n"
                + "    [ ! -d \"$target\" ] || { printf 'Expected a file: %s\\n' \"$relative\" >&2; return 1; }\n"
                + "    mkdir -p -- \"$oma_backup/$(dirname -- \"$relative\")\"\n"
                + "    cp -P -- \"$target\" \"$oma_backup/$relative\"\n"
                + "  fi\n"
                + "  mkdir -p -- \"$(dirname -- \"$target\")\"\n"
                + "  oma_tmp=$(mktemp \"$(dirname -- \"$target\")/.oma-write.XXXXXX\")\n"
                + "  printf %s \"$contents\" > \"$oma_tmp\"\n"
                + "  mv -f -- \"$oma_tmp\" \"$target\"\noma_tmp=''\n}\n");
        for (Map.Entry<String, String> file : seeded().entrySet()) appendWrite(script, file, "seed");
        for (Map.Entry<String, String> file : owned(palette).entrySet()) appendWrite(script, file, "owned");
        script.append("if command -v termux-reload-settings >/dev/null 2>&1; then termux-reload-settings || printf 'Colors saved; reopen Termux to reload them.\\n' >&2; fi\n")
                .append("printf 'Backup: %s\\n' \"$oma_backup\"\n")
                .append("printf 'OMA_THEME_APPLIED=%s\\n' ").append(Commands.quote(palette.key())).append('\n');
        if (script.toString().getBytes(StandardCharsets.UTF_8).length > 100000)
            throw new IllegalArgumentException("Configuration exceeds the native command size limit");
        return script.toString();
    }
    private static void appendWrite(StringBuilder script, Map.Entry<String, String> file, String mode) {
        if (!file.getKey().matches("[a-zA-Z0-9._+/-]+") || file.getKey().contains("..") || file.getKey().startsWith("/"))
            throw new IllegalArgumentException("Invalid config path");
        script.append("oma_write ").append(Commands.quote(file.getKey())).append(' ').append(mode).append(' ')
                .append(Commands.quote(file.getValue())).append('\n');
    }
    static String acknowledged(String kind, String stdout, int exit, String error, boolean truncated) {
        if (!kind.startsWith("omarchy-apply:") || exit != 0 || !error.isEmpty() || truncated) return null;
        String expected = kind.substring("omarchy-apply:".length());
        if (!expected.matches("[a-z0-9][a-z0-9._+-]{0,63}:[0-9a-f]{64}")) return null;
        return stdout.endsWith("OMA_THEME_APPLIED=" + expected + "\n") ? expected : null;
    }
}
