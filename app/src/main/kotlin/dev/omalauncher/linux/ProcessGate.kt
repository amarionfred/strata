package dev.omalauncher.linux

/** Serializes process creation with stopping, so a waiting widget cannot restart Linux. */
internal class ProcessGate {
    private var stopped = false
    @Synchronized fun <T> start(create: () -> T): T {
        check(!stopped) { "Linux is stopped. Resume it from the Linux settings." }
        return create()
    }
    @Synchronized fun stop(kill: () -> Unit) { stopped = true; kill() }
    @Synchronized fun resume() { stopped = false }
}
