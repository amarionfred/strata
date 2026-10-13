package dev.omalauncher.companion;

/** Shell construction for the native runtime. Management never accepts shell text. */
public final class Commands {
    private Commands() {}
    public static final String PREFIX = "/data/data/com.termux/files/usr";
    public static final String HOME = "/data/data/com.termux/files/home";
    public static final String CONFIG = ".config/oma/session.sh";
    public static final String DEFAULT_CONFIG = "# Loaded by Strata command jobs. Native Termux user permissions apply.\n"
            + "export EDITOR=nano\n# Add your aliases, exports, or explicit source commands here.\n";

    public static String quote(String value) {
        if (value == null || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid shell argument");
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static String install(String name) {
        if (name == null || !name.matches("[a-z0-9][a-z0-9+.-]{0,79}"))
            throw new IllegalArgumentException("Enter one package name, such as python, git, or nano.");
        return "exec " + PREFIX + "/bin/pkg install -y " + quote(name);
    }

    public static String terminal(String command) {
        if (command == null || command.trim().isEmpty() || command.length() > 16000 || command.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Enter a command (up to 16,000 characters).");
        return "if [ -r \"$HOME/" + OmarchyConfigs.BASH + "\" ]; then . \"$HOME/" + OmarchyConfigs.BASH + "\" || exit $?; fi\n"
                + "if [ -r \"$HOME/" + CONFIG + "\" ]; then . \"$HOME/" + CONFIG + "\" || exit $?; fi\n"
                + "shopt -s expand_aliases\n" + command;
    }

    public static String interactive() {
        return "if [ -r \"$HOME/" + OmarchyConfigs.INTERACTIVE + "\" ]; then exec bash --rcfile \"$HOME/" + OmarchyConfigs.INTERACTIVE + "\" -i; else exec bash -l; fi";
    }

    public static String readConfig() {
        return "if [ -f \"$HOME/" + CONFIG + "\" ]; then "
                + "[ \"$(wc -c < \"$HOME/" + CONFIG + "\")\" -le 16000 ] || { echo 'Config exceeds 16000 bytes' >&2; exit 1; }; "
                + "cat -- \"$HOME/" + CONFIG + "\"; else printf %s " + quote(DEFAULT_CONFIG) + "; fi";
    }

    public static String saveConfig(String contents) {
        return saveConfigAt(contents, Commands.HOME);
    }

    static String saveConfigAt(String contents, String directory) {
        if (contents == null || contents.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16000)
            throw new IllegalArgumentException("Config must be at most 16,000 UTF-8 bytes.");
        String configDirectory = quote(directory + "/.config/oma");
        String target = quote(directory + "/" + CONFIG);
        String previous = quote(directory + "/" + CONFIG + ".previous");
        return "set -eu\numask 077\nmkdir -p -- " + configDirectory + "\n"
                + "oma_config_tmp=$(mktemp " + configDirectory + "/.session.XXXXXX)\n"
                + "trap 'rm -f -- \"$oma_config_tmp\"' EXIT\n"
                + "printf %s " + quote(contents) + " > \"$oma_config_tmp\"\n"
                + "bash -n \"$oma_config_tmp\"\n"
                + "if [ -f " + target + " ]; then cp -- " + target + " " + previous + "; fi\n"
                + "mv -- \"$oma_config_tmp\" " + target + "\nprintf 'Saved %s\\n' " + target;
    }

    public static String inspect() {
        return "id; uname -a; printf '\\nHome: %s\\nPrefix: %s\\n' \"$HOME\" \"$PREFIX\"; "
                + "command -v pkg; df -h \"$HOME\"";
    }
}
