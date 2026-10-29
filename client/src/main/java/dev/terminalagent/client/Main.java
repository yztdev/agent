package dev.terminalagent.client;

import java.io.Console;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import javax.net.ssl.*;

public class Main {
    static String json(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> {
                    if (c < 32) result.append(String.format("\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }

    static URI serverUrl(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")))
            throw new IllegalArgumentException("Use an HTTPS server address, for example https://192.168.1.100:8443");
        return URI.create("https://" + (uri.getHost().contains(":") ? "[" + uri.getHost().replace("[", "").replace("]", "") + "]" : uri.getHost()) + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
    }

    static SSLContext certificateContext(InputStream source) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        try (InputStream in = source) {
            var certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (certificates.isEmpty()) throw new IllegalArgumentException("No certificate found");
            int i = 0;
            for (var certificate : certificates) store.setCertificateEntry("server-" + i++, certificate);
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);
        return context;
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("Usage: agent connect https://HOST:PORT [--ca-cert trusted-server.pem]");
            return;
        }
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run this program in an interactive terminal");
        String appData = System.getenv("APPDATA");
        Path configPath = (appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home"), ".terminal-agent") : Path.of(appData, "terminal-agent")).resolve("client.properties");
        Properties config = new Properties();
        if (Files.isRegularFile(configPath)) try (InputStream in = Files.newInputStream(configPath)) {
            config.load(in);
        }
        String url = System.getenv("AGENT_SERVER_URL");
        Path certificate = null;
        if (url == null || url.isBlank()) url = config.getProperty("server");
        if (config.getProperty("certificate") != null) certificate = Path.of(config.getProperty("certificate"));
        if (args.length > 0) {
            if ((args.length != 2 && args.length != 4) || !args[0].equals("connect") || (args.length == 4 && !args[2].equals("--ca-cert")))
                throw new IllegalArgumentException("Usage: agent connect https://HOST:PORT [--ca-cert trusted-server.pem]");
            url = args[1];
            certificate = args.length == 4 ? Path.of(args[3]).toAbsolutePath().normalize() : null;
        }
        if (url == null || url.isBlank()) {
            url = console.readLine("Server HTTPS address (e.g. https://192.168.1.100:8443): ");
            if (url == null || url.isBlank()) return;
            if (!"119.29.165.28".equals(serverUrl(url).getHost())) {
                String cert = console.readLine("Trusted certificate path (Enter for system trust): ");
                if (cert != null && !cert.isBlank()) certificate = Path.of(cert).toAbsolutePath().normalize();
            }
        }
        URI server = serverUrl(url);
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (certificate != null) builder.sslContext(certificateContext(Files.newInputStream(certificate)));
        else if ("119.29.165.28".equals(server.getHost())) {
            InputStream trusted = Main.class.getResourceAsStream("/trusted-server.crt");
            if (trusted == null) throw new IllegalStateException("Bundled server certificate is missing");
            builder.sslContext(certificateContext(trusted));
        }
        HttpClient http = builder.build();
        String token = null;
        try {
            token = WindowsCredentials.read(server.toString());
            if (token != null) {
                var check = http.send(HttpRequest.newBuilder(server.resolve("/api/me")).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (check.statusCode() == 401) {
                    WindowsCredentials.delete(server.toString());
                    token = null;
                } else if (check.statusCode() != 200)
                    throw new IllegalStateException("Session check failed: " + check.statusCode());
                else System.out.println("Existing login restored.");
            }
        } catch (UnsatisfiedLinkError | Exception e) {
            System.err.println("Credential storage unavailable: " + e.getMessage());
            token = null;
        }
        if (token == null) {
            String name = console.readLine("Username: ");
            char[] password = console.readPassword("Password: ");
            if (name == null || password == null) return;
            String body;
            try {
                body = "{\"username\":" + json(name) + ",\"password\":" + json(new String(password)) + "}";
            } finally {
                Arrays.fill(password, '\0');
            }
            var response = http.send(HttpRequest.newBuilder(server.resolve("/api/login")).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Login failed: " + response.statusCode());
                return;
            }
            var match = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
            if (!match.find()) throw new IllegalStateException("Invalid login response");
            token = match.group(1);
            try {
                WindowsCredentials.write(server.toString(), token);
            } catch (UnsatisfiedLinkError | Exception e) {
                System.err.println("Could not save login; this session is temporary: " + e.getMessage());
            }
        }
        config.setProperty("server", server.toString());
        if (certificate == null) config.remove("certificate");
        else config.setProperty("certificate", certificate.toString());
        try {
            Files.createDirectories(configPath.getParent());
            try (var out = Files.newOutputStream(configPath)) {
                config.store(out, "Terminal Agent connection settings");
            }
        } catch (java.io.IOException e) {
            System.err.println("Could not save connection settings: " + e.getMessage());
        }
        System.out.println("Logged in. Commands: /status, /quota, /logout, /exit.");
        String line;
        while ((line = console.readLine("> ")) != null) {
            if (line.equals("/status")) {
                var res = http.send(HttpRequest.newBuilder(server.resolve("/api/me")).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
                System.out.println(res.statusCode() == 200 ? res.body() : "Session unavailable: " + res.statusCode());
            } else if (line.equals("/quota")) {
                var res = http.send(HttpRequest.newBuilder(server.resolve("/api/quota")).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    var balance = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.body());
                    System.out.printf("总额度: %s  已用: %s  预留: %s  可用: %s%n",
                            balance.path("quota_total"), balance.path("settled_total"),
                            balance.path("reserved_total"), balance.path("available"));
                } else {
                    System.err.println("无法查询额度: HTTP " + res.statusCode());
                }
            } else if (line.equals("/logout")) {
                var res = http.send(HttpRequest.newBuilder(server.resolve("/api/logout")).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                if (res.statusCode() == 200 || res.statusCode() == 401) {
                    try {
                        WindowsCredentials.delete(server.toString());
                    } catch (Exception e) {
                        System.err.println("Could not remove saved login: " + e.getMessage());
                    }
                    System.out.println("Logged out");
                } else System.err.println("Logout failed; saved credentials retained: " + res.statusCode());
                return;
            } else if (line.equals("/exit")) return;
            else if (!line.isBlank() && !line.startsWith("/")) {
                Boolean optimize = OptimizationChoice.ask(console);
                if (optimize == null) return;
                ChatClient.ask(http, server, token, line, optimize, console);
            } else if (!line.isBlank()) System.out.println("Unknown command.");
        }
    }
}
