import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Full HTTP/WebSocket integration check with only JDK 25; no browser or extra libraries. */
public class SmokeCheck {
    static final URI BASE = URI.create("http://127.0.0.1:8080");
    static final String KEY = "local-e2e-ingestion-key-not-for-deployment";
    static final StringBuilder SERVER_LOG = new StringBuilder();

    public static void main(String[] args) throws Exception {
        var command = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-jar", "target/payara-micro.jar",
                "--deploy", "target/websocket-lab.war", "--contextroot", "/", "--port", "8080", "--nocluster").redirectErrorStream(true);
        command.environment().put("DEMO_INGEST_KEY", KEY);
        Process server = command.start();
        Thread.ofVirtual().start(() -> {
            try (var reader = server.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (SERVER_LOG) { SERVER_LOG.append(line).append('\n'); }
                }
            } catch (Exception ignored) { }
        });
        try {
            waitForServer(server);
            System.out.println("PASS: Payara started and health endpoint is ready.");
            Client alice = new Client();
            Client bob = new Client();
            String alicePage = alice.login("alice", System.getenv().getOrDefault("DEMO_PASSWORD", "Demo123!"));
            String bobPage = bob.login("bob", System.getenv().getOrDefault("DEMO_PASSWORD", "Demo123!"));
            if (!alicePage.contains("Network overview")) java.nio.file.Files.writeString(java.nio.file.Path.of("target/smoke-alice-response.html"), alicePage);
            if (!bobPage.contains("Network overview")) java.nio.file.Files.writeString(java.nio.file.Path.of("target/smoke-bob-response.html"), bobPage);
            check(alicePage.contains("Network overview") && bobPage.contains("Network overview"), "Both accounts must reach the rendered dashboard.");
            check(!bobPage.contains("Apply change"), "Viewer must not see administrator actions.");
            System.out.println("PASS: Alice and Bob can sign in; viewer/admin presentation is correct.");

            var aliceDashboard = new Inbox();
            var bobDashboard = new Inbox();
            var alicePersonal = new Inbox();
            var bobPersonal = new Inbox();
            WebSocket firstSocket = alice.connect(alicePage, "dashboard", aliceDashboard);
            WebSocket bobSocket = bob.connect(bobPage, "dashboard", bobDashboard);
            WebSocket privateSocket = alice.connect(alicePage, "personal", alicePersonal);
            WebSocket bobPrivateSocket = bob.connect(bobPage, "personal", bobPersonal);
            check(aliceDashboard.await("channel-ready", 10), "Initial dashboard ready handshake must arrive.");
            check(bobDashboard.await("channel-ready", 10), "Bob dashboard handshake must arrive.");
            check(alicePersonal.await("channel-ready", 10), "Alice private handshake must arrive.");
            check(bobPersonal.await("channel-ready", 10), "Bob private handshake must arrive.");
            System.out.println("PASS: All four authenticated WebSocket connections receive ready messages.");

            check(alice.ingest(null).statusCode() == 401, "Ingestion must reject missing credentials.");
            check(alice.ingest(KEY).statusCode() == 202, "Valid external telemetry must be accepted.");
            check(aliceDashboard.await("dashboard-changed", 10), "Alice must receive broadcast update.");
            check(bobDashboard.await("dashboard-changed", 10), "Bob must receive broadcast update.");
            System.out.println("PASS: API authentication and server-to-browser shared push work.");

            Map<String, String> notification = hiddenFields(alicePage);
            notification.put("board", "board");
            notification.put("board:notifyMe", "board:notifyMe");
            notification.put("jakarta.faces.partial.ajax", "true");
            notification.put("jakarta.faces.source", "board:notifyMe");
            notification.put("jakarta.faces.partial.execute", "board:notifyMe");
            notification.put("jakarta.faces.partial.render", "board:notifications");
            var partial = alice.post("/app/dashboard.xhtml", notification, true);
            check(partial.statusCode() == 200 && !partial.body().contains("<error>"), "Private notification action must complete without a Faces error.");
            check(alicePersonal.await("private-notification", 10), "Alice must receive private notification.");
            check(!bobPersonal.await("private-notification", 2), "Bob must not receive Alice's notification.");
            System.out.println("PASS: Personal push is delivered only to the requested user.");

            firstSocket.sendClose(1000, "Integration reconnect test").join();
            var reconnected = new Inbox();
            WebSocket nextSocket = alice.connect(alicePage, "dashboard", reconnected);
            check(reconnected.await("channel-ready", 10), "Reconnected socket must receive a new resync handshake.");
            System.out.println("PASS: Reopening a socket receives the reconnect resynchronization handshake.");

            String fresh = alice.get("/app/dashboard.xhtml").body();
            var logout = hiddenFields(fresh);
            logout.put("accountForm", "accountForm");
            logout.put("accountForm:logout", "Sign out");
            alice.post("/app/dashboard.xhtml", logout, false);
            check(alice.get("/app/dashboard.xhtml").body().contains("loginForm:username"), "Logout must protect subsequent dashboard requests.");
            Client invalid = new Client();
            String rejected = invalid.login("alice", "wrong-password");
            check(rejected.contains("loginForm:username") && !rejected.contains("Network overview"), "Invalid credentials must return a usable sign-in page, not a blank response.");
            check(invalid.login("alice", System.getenv().getOrDefault("DEMO_PASSWORD", "Demo123!")).contains("Network overview"), "A valid retry after failed credentials must succeed.");
            System.out.println("PASS: Logout and invalid-credential protection work.");
            nextSocket.abort(); bobSocket.abort(); privateSocket.abort(); bobPrivateSocket.abort();
            System.out.println("All live HTTP/WebSocket checks passed.");
        } catch (Throwable failure) {
            synchronized (SERVER_LOG) { System.err.println(SERVER_LOG.substring(Math.max(0, SERVER_LOG.length() - 18000))); }
            throw failure;
        } finally {
            server.destroy();
            if (!server.waitFor(15, TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    static void waitForServer(Process server) throws Exception {
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        while (System.nanoTime() < deadline && server.isAlive()) {
            try {
                var result = client.send(HttpRequest.newBuilder(BASE.resolve("/api/health")).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (result.statusCode() == 200 && result.body().contains("UP")) return;
            } catch (Exception ignored) { }
            Thread.sleep(1000);
        }
        throw new AssertionError("Payara did not become ready in three minutes.");
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static Map<String, String> hiddenFields(String html) {
        var result = new LinkedHashMap<String, String>();
        var inputs = Pattern.compile("<input\\b[^>]*>").matcher(html);
        while (inputs.find()) {
            String tag = inputs.group();
            if (!tag.contains("type=\"hidden\"")) continue;
            var name = Pattern.compile("name=\"([^\"]+)\"").matcher(tag);
            var value = Pattern.compile("value=\"([^\"]*)\"").matcher(tag);
            if (name.find() && value.find()) result.put(name.group(1), value.group(1).replace("&amp;", "&"));
        }
        return result;
    }

    static class Client {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(cookies).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> get(String path) throws Exception { return http.send(HttpRequest.newBuilder(BASE.resolve(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        String login(String username, String password) throws Exception {
            var loginPage = get("/login.xhtml");
            check(loginPage.statusCode() == 200 && loginPage.body().contains("loginForm:username"),
                    "Login page must render before form submission (HTTP " + loginPage.statusCode() + ", " + loginPage.body().length() + " chars).");
            var fields = hiddenFields(loginPage.body());
            fields.put("loginForm", "loginForm");
            fields.put("loginForm:username", username);
            fields.put("loginForm:password", password);
            fields.put("loginForm:signIn", "Sign in");
            var signedIn = post("/login.xhtml", fields, false);
            var dashboard = get("/app/dashboard.xhtml");
            System.out.println("Sign-in check (" + username + "): form HTTP " + signedIn.statusCode() + ", dashboard HTTP " + dashboard.statusCode() + ", " + dashboard.body().length() + " chars");
            return dashboard.body();
        }
        HttpResponse<String> post(String path, Map<String, String> fields, boolean ajax) throws Exception {
            String body = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
            var request = HttpRequest.newBuilder(BASE.resolve(path)).header("Content-Type", "application/x-www-form-urlencoded");
            if (ajax) request.header("Faces-Request", "partial/ajax");
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> ingest(String key) throws Exception {
            var request = HttpRequest.newBuilder(BASE.resolve("/api/ingest")).header("Content-Type", "application/json");
            if (key != null) request.header("X-Ingest-Key", key);
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString("{\"nodeId\":\"node-2\",\"load\":88,\"status\":\"DEGRADED\"}")).build(), HttpResponse.BodyHandlers.ofString());
        }
        WebSocket connect(String html, String channel, Inbox inbox) {
            var match = Pattern.compile("(?:wss?://[^\\\"']+)?/jakarta\\.faces\\.push/" + channel + "\\?[^\\\"']+").matcher(html);
            check(match.find(), "Rendered page must contain a WebSocket URL for " + channel);
            String url = match.group().replace("&amp;", "&");
            if (!url.startsWith("ws")) url = "ws://127.0.0.1:8080" + url;
            String cookie = cookies.getCookieStore().getCookies().stream().map(c -> c.getName() + "=" + c.getValue()).collect(java.util.stream.Collectors.joining("; "));
            return http.newWebSocketBuilder().header("Cookie", cookie).connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(url), inbox).join();
        }
        static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    }

    static class Inbox implements WebSocket.Listener {
        final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder fragments = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) { messages.offer(fragments.toString()); fragments.setLength(0); }
            socket.request(1); return null;
        }
        boolean await(String type, int seconds) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
            while (System.nanoTime() < deadline) {
                String message = messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (message == null) return false;
                if (message.contains("\"" + type + "\"")) return true;
            }
            return false;
        }
    }
}
