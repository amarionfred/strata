package dev.omalauncher.linux

import org.junit.Assert.*
import org.junit.Test

class ProcessGateTest {
    @Test fun stoppedSessionCannotBeRestartedByAQueuedWidget() {
        val gate = ProcessGate()
        var children = 0
        gate.start { children++ }
        gate.stop { children = 0 }
        repeat(3) { assertTrue(runCatching { gate.start { children++ } }.isFailure) }
        assertEquals(0, children)
        gate.resume()
        gate.start { children++ }
        assertEquals(1, children)
    }
    @Test fun aFailedKillStillBlocksNewProcesses() {
        val gate = ProcessGate()
        runCatching { gate.stop { error("cannot kill") } }
        assertTrue(runCatching { gate.start { fail("must stay stopped") } }.isFailure)
    }
}
