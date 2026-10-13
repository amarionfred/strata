package dev.omalauncher.linux

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BubblewrapTest {
    @Test fun isolationRequestsNeverExecuteTheirPayload() {
        val shim = File("src/main/assets/bwrap-shim.sh").absolutePath
        for (options in listOf(emptyList(), listOf("--ro-bind", "/", "/", "--unshare-all", "--"), listOf("--unknown-option"))) {
            val process = ProcessBuilder(listOf("sh", shim) + options + listOf("sh", "-c", "printf PAYLOAD_EXECUTED"))
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(78, process.waitFor())
            assertTrue(output.contains("command was not run"))
            assertFalse(output.contains("PAYLOAD_EXECUTED"))
        }
    }
}
