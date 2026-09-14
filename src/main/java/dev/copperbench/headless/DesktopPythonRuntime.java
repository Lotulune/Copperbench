package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.platform.PrivatePathPermissions;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local scripting access to the desktop's existing Core and writer lease. No MCP dependency. */
public final class DesktopPythonRuntime implements AutoCloseable {
    private final ServerSocket server;
    private final Path connectionFile;
    private final String token;
    private final NativeApiSession api;
    private final PythonContext context;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(8);
    private final ExecutorService workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    private final AtomicBoolean closed = new AtomicBoolean();

    private DesktopPythonRuntime(Path workspaceFile, UUID workspaceId, HeadlessWorkspaceEntryAdapter adapter)
            throws IOException {
        context = new PythonContext(adapter, workspaceId);
        api = new NativeApiSession(adapter, workspaceId, context);
        connectionFile = connectionFile(workspaceFile);
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        token = HexFormat.of().formatHex(secret);
        server = new ServerSocket();
        try {
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            publish(workspaceId);
            workers.submit(this::accept);
        } catch (IOException | RuntimeException exception) {
            server.close();
            workers.shutdownNow();
            throw exception;
        }
    }

    public static DesktopPythonRuntime start(Path workspaceFile, UUID workspaceId,
            HeadlessWorkspaceEntryAdapter adapter) throws IOException {
        return new DesktopPythonRuntime(workspaceFile, workspaceId, adapter);
    }

    public PythonContext context() { return context; }

    /** Credentials live in the OS user's home, never in a shareable workspace. */
    public static Path connectionFile(Path workspaceFile) throws IOException {
        String key = workspaceFile.toRealPath().toString().replace('\\', '/');
        if (System.getProperty("os.name").startsWith("Windows")) key = key.toLowerCase(Locale.ROOT);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
            return Path.of(System.getProperty("user.home"), ".copperbench", "python-sessions", hash + ".json");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void publish(UUID workspaceId) throws IOException {
        Path directory = connectionFile.getParent();
        if (Files.isSymbolicLink(directory)) throw new IOException("Python session directory cannot be a symlink");
        PrivatePathPermissions.createPrivateDirectory(directory);
        // POSIX mode bits do not protect files on Windows. Restrict the ACL
        // before creating the credential, and propagate it to child files.
        AclFileAttributeView acl = Files.getFileAttributeView(directory, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT).build()));
        } else if (!PrivatePathPermissions.posixSupported(directory)) {
            throw new IOException("Owner-only Python credential permissions are unavailable");
        }
        JsonObject connection = new JsonObject();
        connection.addProperty("nativeApiVersion", "1");
        connection.addProperty("workspaceId", workspaceId.toString());
        connection.addProperty("port", server.getLocalPort());
        connection.addProperty("token", token);
        Path temporary = Files.createTempFile(directory, "session-", ".tmp");
        try {
            PrivatePathPermissions.makePrivateFile(temporary);
            Files.writeString(temporary, connection.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, connectionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, connectionFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void accept() {
        while (!closed.get()) {
            try {
                Socket client = server.accept();
                if (!slots.tryAcquire()) { client.close(); continue; }
                clients.add(client);
                if (closed.get()) {
                    clients.remove(client);
                    slots.release();
                    client.close();
                    break;
                }
                try {
                    workers.submit(() -> serve(client));
                } catch (RejectedExecutionException exception) {
                    clients.remove(client);
                    slots.release();
                    client.close();
                }
            } catch (IOException exception) {
                if (!closed.get()) org.apache.logging.log4j.LogManager.getLogger(DesktopPythonRuntime.class)
                        .warn("Python session listener stopped", exception);
                break;
            }
        }
    }

    private void serve(Socket client) {
        try (client) {
            client.setSoTimeout(5000);
            var input = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            var output = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
            StringBuilder hello = new StringBuilder();
            int ch;
            while ((ch = input.read()) != -1 && ch != '\n' && hello.length() < 4096) hello.append((char) ch);
            if (ch != '\n' || !authenticated(hello.toString())) {
                output.println("{\"nativeApiVersion\":\"1\",\"status\":\"rejected\"}");
                output.flush();
                return;
            }
            client.setSoTimeout(0);
            api.serve(input, output);
        } catch (IOException ignored) {
            // A client disconnect or authentication timeout never closes the workspace.
        } finally {
            clients.remove(client);
            slots.release();
        }
    }

    private boolean authenticated(String hello) {
        try {
            String supplied = JsonParser.parseString(hello).getAsJsonObject().get("token").getAsString();
            return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { server.close(); } catch (IOException ignored) { }
        for (Socket client : clients) {
            try { client.close(); } catch (IOException ignored) { }
        }
        workers.shutdownNow();
        try {
            workers.awaitTermination(5, TimeUnit.SECONDS);
            // Do not erase a newer session's discovery record.
            if (Files.exists(connectionFile) && authenticated(Files.readString(connectionFile)))
                Files.deleteIfExists(connectionFile);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            org.apache.logging.log4j.LogManager.getLogger(DesktopPythonRuntime.class)
                    .warn("Could not remove Python session credentials", exception);
        }
    }
}
