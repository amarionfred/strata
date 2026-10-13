package dev.omalauncher.companion;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Checks the actual Java client, including refusal of an impersonated runtime. */
public class WorkspaceConnectionTest {
    @Test public void hmacMatchesIndependentSha256Vector() throws Exception {
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                WorkspaceConnection.proof("key", "The quick brown fox jumps over the lazy dog"));
    }
    @Test public void authenticatedRequestAndUnicodeResponseRoundTrip() throws Exception {
        exchange(false);
    }
    @Test public void fakeRuntimeCannotReceiveWorkspaceCommands() throws Exception {
        exchange(true);
    }
    private static void exchange(boolean impersonate) throws Exception {
        String token = WorkspaceConnection.randomToken(), challenge = WorkspaceConnection.randomToken();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<?> served = worker.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    output.println(new JSONObject().put("challenge",challenge).put("protocol",1));
                    JSONObject auth = new JSONObject(input.readLine());
                    String message = challenge + ":" + auth.getString("nonce");
                    assertEquals(WorkspaceConnection.proof(token,"client:" + message),auth.getString("auth"));
                    output.println(new JSONObject().put("authenticated",impersonate ? "0".repeat(64) : WorkspaceConnection.proof(token,"server:" + message)));
                    if (impersonate) assertNull(input.readLine());
                    else {
                        assertEquals("status",new JSONObject(input.readLine()).getString("op"));
                        output.println(new JSONObject().put("ok",true).put("home","café / λ"));
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            if (impersonate) assertThrows(IOException.class, () -> new WorkspaceConnection(server.getLocalPort(),token));
            else try (WorkspaceConnection client = new WorkspaceConnection(server.getLocalPort(),token)) {
                client.send(new JSONObject().put("op","status"));
                assertEquals("café / λ",client.read().getString("home"));
            }
            served.get(10,TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
