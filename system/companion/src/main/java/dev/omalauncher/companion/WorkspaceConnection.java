package dev.omalauncher.companion;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Mutually authenticated, bounded JSON framing. Never sends the pairing secret. */
final class WorkspaceConnection implements Closeable {
    static final int PROTOCOL = 1, MAX_FRAME = 512 * 1024;
    private final Socket socket = new Socket();
    private final InputStream input;
    private final OutputStream output;

    WorkspaceConnection(int port, String token) throws Exception {
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 1500);
            socket.setSoTimeout(5000);
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
            JSONObject hello = read();
            String challenge = hello.getString("challenge"), nonce = randomToken();
            if (hello.getInt("protocol") != PROTOCOL || !challenge.matches("[a-f0-9]{64}"))
                throw new IOException("Workspace protocol mismatch");
            String message = challenge + ":" + nonce;
            send(new JSONObject().put("nonce", nonce).put("auth", proof(token, "client:" + message)));
            String expected = proof(token, "server:" + message);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    read().optString("authenticated").getBytes(StandardCharsets.UTF_8)))
                throw new IOException("Workspace authentication failed");
            socket.setSoTimeout(30000);
        } catch (Exception error) { socket.close(); throw error; }
    }
    static String randomToken() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return hex(bytes);
    }
    static String proof(String token, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
    synchronized void send(JSONObject value) throws Exception {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= MAX_FRAME) throw new IOException("Workspace request too large");
        output.write(bytes); output.write('\n'); output.flush();
    }
    JSONObject read() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != '\n') {
            if (value < 0) throw new EOFException("Workspace disconnected");
            if (bytes.size() >= MAX_FRAME) throw new IOException("Workspace response too large");
            bytes.write(value);
        }
        JSONObject result = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        if (result.has("ok") && !result.optBoolean("ok")) throw new IOException(result.optString("error", "Workspace action failed"));
        return result;
    }
    void streaming() throws SocketException { socket.setSoTimeout(0); }
    @Override public void close() throws IOException { socket.close(); }
}
