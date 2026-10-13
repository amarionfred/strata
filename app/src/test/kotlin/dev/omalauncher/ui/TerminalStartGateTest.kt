package dev.omalauncher.ui

import org.junit.Assert.*
import org.junit.Test

class TerminalStartGateTest {
    @Test fun returningToAnExitedTileCannotAutoRestartIt() {
        val gate = TerminalStartGate()
        assertTrue(gate.firstAppearance())
        repeat(10) { assertFalse(gate.firstAppearance()) }
        gate.explicitlyStarted()
        assertFalse(gate.firstAppearance())
        assertTrue(TerminalStartGate().firstAppearance())
    }
    @Test fun explicitStartBeforeFirstCompositionCounts() {
        val gate = TerminalStartGate()
        gate.explicitlyStarted()
        assertFalse(gate.firstAppearance())
    }
}
