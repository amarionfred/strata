package dev.omalauncher.companion;

/** Commands sent to the phone's existing root manager. */
final class RootCommands {
    static final String CONTROL = "/data/adb/modules/oma_native_system/omactl";
    static final String ROOT = "/data/adb/oma-system/rootfs";
    static final String VERIFIED = "STRATA_UID_0";
    private RootCommands() {}

    static String command(String text) {
        if (text == null || text.trim().isEmpty() || text.length() > 16000 || text.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Enter a command of up to 16,000 characters.");
        return text;
    }

    static String guarded(String script) {
        return guard(script, "/system/bin/id", "/system/bin/sh");
    }

    static String guard(String script, String idBinary, String shell) {
        return "[ \"$(" + Commands.quote(idBinary) + " -u)\" = 0 ] || { printf '%s\\n' 'Root unavailable: allow Strata in an installed root manager.' >&2; exit 126; }\n"
                + "printf '%s\\n' " + Commands.quote(VERIFIED) + "\n"
                + "exec " + Commands.quote(shell) + " -c " + Commands.quote(command(script));
    }

    static String inspect() {
        return "/system/bin/id; /system/bin/uname -a; /system/bin/getenforce; "
                + "printf '\\nRuntime: '; if [ -x " + Commands.quote(CONTROL) + " ]; then "
                + Commands.quote(CONTROL) + " status; else printf '%s\\n' 'module not installed'; fi";
    }

    static String control(String action) {
        if (!java.util.Arrays.asList("start", "stop", "status", "autostart on", "autostart off").contains(action))
            throw new IllegalArgumentException("Unknown runtime action");
        return requireRuntime() + "exec " + Commands.quote(CONTROL) + " " + action;
    }

    private static String requireRuntime() {
        return "[ -x " + Commands.quote(CONTROL) + " ] || { printf '%s\\n' 'Install the Strata runtime module in Magisk, reboot, then start Linux in System.' >&2; exit 127; }\n";
    }

    static String linux(String... arguments) {
        StringBuilder result = new StringBuilder(requireRuntime()).append("exec ").append(Commands.quote(CONTROL)).append(" exec");
        for (String argument : arguments) result.append(' ').append(Commands.quote(argument));
        return result.toString();
    }

    static String linuxShell(String command) { return linux("/bin/sh", "-lc", command(command)); }

    static String interactive(boolean linux) {
        String script = linux ? requireRuntime() + "exec " + Commands.quote(CONTROL) + " shell"
                : "cd /; export HOME=/; exec /system/bin/sh -i";
        return "unset LD_PRELOAD LD_LIBRARY_PATH; exec su -c " + Commands.quote(guarded(script));
    }

    static String install(String name) {
        if (name == null || !name.matches("[a-z0-9][a-z0-9+_.-]{0,79}"))
            throw new IllegalArgumentException("Enter one Linux package name, such as sudo, python3, or git.");
        return linux("/sbin/apk", "add", "--", name);
    }

    static String serviceName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Use letters, numbers, underscores, or dashes for the service name.");
        return name;
    }

    static String saveService(String name, String script) {
        return linuxShell(saveServiceAt(name, script, "/etc/oma-system/services"));
    }

    static String saveServiceAt(String name, String script, String directory) {
        String file = directory + "/" + serviceName(name);
        String backup = directory + "/." + name + ".previous";
        String body = "#!/bin/sh\n" + command(script) + "\n";
        return "set -eu\numask 077\nmkdir -p " + Commands.quote(directory) + "\n"
                + "strata_tmp=$(mktemp " + Commands.quote(directory + "/.draft.XXXXXX") + ")\n"
                + "trap 'rm -f -- \"$strata_tmp\"' EXIT\n"
                + "printf %s " + Commands.quote(body) + " > \"$strata_tmp\"\n"
                + "/bin/sh -n \"$strata_tmp\"\nchmod 700 \"$strata_tmp\"\n"
                + "if [ -e " + Commands.quote(file) + " ]; then cp -P -- " + Commands.quote(file) + " " + Commands.quote(backup) + "; fi\n"
                + "mv -f -- \"$strata_tmp\" " + Commands.quote(file) + "\n"
                + "printf '%s\\n' 'Service saved. Stop and start Linux to load the change.'";
    }

    static String serviceEnabled(String name, boolean enabled) {
        return linux("/bin/chmod", enabled ? "700" : "600", "/etc/oma-system/services/" + serviceName(name));
    }

    static String files(String path, boolean read) {
        if (path == null || !path.startsWith("/") || path.length() > 4096 || path.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Enter an absolute path.");
        return "exec /system/bin/" + (read ? "head -c 60000 -- " : "ls -la -- ") + Commands.quote(path);
    }
}
