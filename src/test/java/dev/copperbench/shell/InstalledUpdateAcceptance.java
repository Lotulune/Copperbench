package dev.copperbench.shell;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import dev.copperbench.ProductIdentity;
import dev.copperbench.installer.UpdatePreparation;
import net.mcreator.Launcher;
import net.mcreator.ui.workspace.selector.WorkspaceSelector;
import javax.net.ssl.*;
import javax.swing.*;
import java.awt.Window;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.*;

/** Installed-product acceptance driver. Only the network source is injected; no test endpoint enters the release UI. */
public final class InstalledUpdateAcceptance {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]).toAbsolutePath(), output = Path.of(args[1]).toAbsolutePath();
        String version = args[2];
        KeyStore keys = KeyStore.getInstance("PKCS12");
        char[] password = Files.readString(output.resolve("tls-password.txt")).trim().toCharArray();
        try (InputStream input = Files.newInputStream(output.resolve("localhost.p12"))) { keys.load(input, password); }
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(keys, password);
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tm.init(keys);
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        JsonObject release = new JsonObject(); release.addProperty("tag_name", "v" + version); release.addProperty("draft", false); release.addProperty("prerelease", false);
        JsonArray entries = new JsonArray();
        try (var files = Files.list(assets)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                JsonObject asset = new JsonObject(); asset.addProperty("name", file.getFileName().toString());
                asset.addProperty("browser_download_url", "https://github.com/Lotulune/Copperbench/releases/download/v" + version + "/" + file.getFileName()); entries.add(asset);
            }
        }
        release.add("assets", entries); JsonArray releases = new JsonArray(); releases.add(release);
        Path requests = output.resolve("http-requests.jsonl");
        server.createContext("/", exchange -> {
            String request = exchange.getRequestURI().getPath();
            JsonObject receipt = new JsonObject(); receipt.addProperty("path", request); receipt.addProperty("at", java.time.Instant.now().toString());
            try {
                if (request.equals("/releases")) {
                    byte[] bytes = releases.toString().getBytes(StandardCharsets.UTF_8); receipt.addProperty("bytes", bytes.length);
                    exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                } else {
                    Path file = assets.resolve(request.substring(request.lastIndexOf('/') + 1)).normalize();
                    if (!file.getParent().equals(assets) || !Files.isRegularFile(file)) { exchange.sendResponseHeaders(404, -1); return; }
                    receipt.addProperty("bytes", Files.size(file)); exchange.sendResponseHeaders(200, Files.size(file)); Files.copy(file, exchange.getResponseBody());
                }
                synchronized (requests) { Files.writeString(requests, receipt + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            } finally { exchange.close(); }
        });
        server.setExecutor(Executors.newCachedThreadPool(Thread.ofVirtual().factory())); server.start();
        UpdatePreparation.ContentSource source = remote -> {
            String path = remote.getHost().equals("api.github.com") ? "/releases" : "/assets/" + remote.getPath().substring(remote.getPath().lastIndexOf('/') + 1);
            HttpsURLConnection connection = (HttpsURLConnection) URI.create("https://localhost:" + server.getAddress().getPort() + path).toURL().openConnection();
            connection.setSSLSocketFactory(tls.getSocketFactory()); connection.setConnectTimeout(20_000); connection.setReadTimeout(30_000);
            if (connection.getResponseCode() != 200) throw new IOException("Fixture update source returned " + connection.getResponseCode());
            return new FilterInputStream(connection.getInputStream()) { @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } } };
        };
        Files.writeString(output.resolve("parent-pid.txt"), Long.toString(ProcessHandle.current().pid()));
        Files.writeString(output.resolve("initial-version.txt"), ProductIdentity.VERSION);
        Launcher.main(new String[0]);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        while (System.nanoTime() < deadline) {
            var found = new java.util.concurrent.atomic.AtomicReference<WorkspaceSelector>();
            SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) if (window instanceof WorkspaceSelector selector && selector.isShowing()) found.set(selector); });
            if (found.get() != null) {
                WorkspaceSelector selector = found.get();
                SwingUtilities.invokeLater(() -> ProductUpdateDialog.show(selector, source, selector.getApplication()::closeApplication));
                return;
            }
            Thread.sleep(100);
        }
        throw new IOException("Installed workspace selector did not open");
    }
}
