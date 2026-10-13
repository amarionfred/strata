package dev.omalauncher.ui

/** Leaving/re-entering composition cannot restart a terminal the user already exited. */
internal class TerminalStartGate {
    private var started = false
    fun firstAppearance(): Boolean {
        if (started) return false
        started = true
        return true
    }
    fun explicitlyStarted() { started = true }
}
