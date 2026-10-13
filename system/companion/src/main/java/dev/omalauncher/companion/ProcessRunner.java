package dev.omalauncher.companion;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded native execution for root jobs and the typed administration service. */
final class ProcessRunner {
    static final int MAX_OUTPUT = 65536;
    static final class Result {
        final int exit;
        final String output;
        Result(int exit, String output) { this.exit = exit; this.output = output; }
    }
    static Result run(long timeoutMillis, String... arguments) throws Exception {
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        process.getOutputStream().close();
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "oma-command-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> output = reader.submit(() -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            boolean truncated = false;
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    int available = Math.max(0, MAX_OUTPUT - bytes.size());
                    bytes.write(buffer, 0, Math.min(available, count));
                    if (count > available) truncated = true;
                }
            }
            return bytes.toString(StandardCharsets.UTF_8.name()) + (truncated ? "\n[output truncated]" : "");
        });
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new TimeoutException("Command exceeded " + timeoutMillis + " ms");
            }
            return new Result(process.exitValue(), output.get(1000, TimeUnit.MILLISECONDS));
        } finally {
            process.destroyForcibly();
            output.cancel(true);
            reader.shutdownNow();
        }
    }
}
