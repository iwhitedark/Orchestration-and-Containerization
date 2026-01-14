import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.URLDecoder;

import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SentimentAnalyzer {

    private static final List<String> POSITIVE_WORDS = Arrays.asList(
            "good", "great", "excellent", "happy", "love", "wonderful", "amazing", "best", "beautiful", "fantastic"
    );

    private static final List<String> NEGATIVE_WORDS = Arrays.asList(
            "bad", "terrible", "awful", "hate", "worst", "poor", "horrible", "sad", "ugly", "disgusting"
    );

    // --- simple in-memory metrics (Prometheus-like) ---
    private static final AtomicLong HTTP_REQUESTS_TOTAL = new AtomicLong(0);
    private static final AtomicLong HTTP_REQUESTS_2XX = new AtomicLong(0);
    private static final AtomicLong HTTP_REQUESTS_4XX = new AtomicLong(0);
    private static final AtomicLong HTTP_REQUESTS_5XX = new AtomicLong(0);
    private static final long START_TIME_EPOCH_SECONDS = Instant.now().getEpochSecond();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Health endpoints (often used by Kubernetes probes)
        server.createContext("/health", SentimentAnalyzer::handleHealth);
        server.createContext("/ready", SentimentAnalyzer::handleReady);

        // Main API
        server.createContext("/api/sentiment", SentimentAnalyzer::handleSentiment);

        // Metrics
        server.createContext("/metrics", SentimentAnalyzer::handleMetrics);

        // Optional: simple root page
        server.createContext("/", SentimentAnalyzer::handleRoot);

        // Executor: fixed thread pool (better than null/default)
        int threads = Integer.parseInt(System.getenv().getOrDefault("THREADS", "8"));
        server.setExecutor(Executors.newFixedThreadPool(threads));

        server.start();
        System.out.println("Server started on port " + port + " with " + threads + " threads");
    }

    // ----------------- Handlers -----------------

    private static void handleRoot(HttpExchange exchange) throws IOException {
        countRequest();
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                count4xx();
                return;
            }

            String body = "{\"service\":\"sentiment\",\"endpoints\":[\"/api/sentiment\",\"/health\",\"/ready\",\"/metrics\"]}";
            sendJson(exchange, 200, body);
            count2xx();
        } catch (Exception e) {
            count5xx();
            sendJson(exchange, 500, "{\"error\":\"Internal Server Error\"}");
        } finally {
            exchange.close();
        }
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        countRequest();
        try {
            // health обычно должен отвечать быстро и всегда, если процесс жив
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                count4xx();
                return;
            }

            sendJson(exchange, 200, "{\"status\":\"UP\"}");
            count2xx();
        } catch (Exception e) {
            count5xx();
            sendJson(exchange, 500, "{\"status\":\"DOWN\"}");
        } finally {
            exchange.close();
        }
    }

    private static void handleReady(HttpExchange exchange) throws IOException {
        countRequest();
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                count4xx();
                return;
            }

            // Здесь обычно проверяют зависимости (БД, очереди).
            // В учебном сервисе просто считаем, что готовы.
            sendJson(exchange, 200, "{\"status\":\"READY\"}");
            count2xx();
        } catch (Exception e) {
            count5xx();
            sendJson(exchange, 500, "{\"status\":\"NOT_READY\"}");
        } finally {
            exchange.close();
        }
    }

    private static void handleSentiment(HttpExchange exchange) throws IOException {
        countRequest();
        long start = System.nanoTime();

        try {
            String method = exchange.getRequestMethod().toUpperCase();

            String text;
            if ("GET".equals(method)) {
                // GET /api/sentiment?text=hello
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                text = query.getOrDefault("text", "");
            } else if ("POST".equals(method)) {
                // POST /api/sentiment  with JSON: {"text":"..."}
                String body = readBody(exchange);
                text = extractTextFromJson(body);
            } else {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                count4xx();
                return;
            }

            String normalized = text.trim();
            String sentiment = analyzeSentiment(normalized.toLowerCase());

            // Minimal JSON escaping to avoid broken JSON if user has quotes/backslashes
            String safeText = jsonEscape(normalized);

            String response = "{\"text\":\"" + safeText + "\",\"sentiment\":\"" + sentiment + "\"}";
            sendJson(exchange, 200, response);
            count2xx();

            long durMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[INFO] " + method + " /api/sentiment " + exchange.getRemoteAddress()
                    + " sentiment=" + sentiment + " durMs=" + durMs);

        } catch (IllegalArgumentException badRequest) {
            count4xx();
            sendJson(exchange, 400, "{\"error\":\"Bad Request\"}");
        } catch (Exception e) {
            count5xx();
            sendJson(exchange, 500, "{\"error\":\"Internal Server Error\"}");
        } finally {
            exchange.close();
        }
    }

    private static void handleMetrics(HttpExchange exchange) throws IOException {
        countRequest();
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, "Method Not Allowed\n", "text/plain; charset=utf-8");
                count4xx();
                return;
            }

            String metrics =
                    "# HELP http_requests_total Total HTTP requests\n" +
                    "# TYPE http_requests_total counter\n" +
                    "http_requests_total " + HTTP_REQUESTS_TOTAL.get() + "\n" +
                    "# HELP http_requests_2xx_total Total 2xx responses\n" +
                    "# TYPE http_requests_2xx_total counter\n" +
                    "http_requests_2xx_total " + HTTP_REQUESTS_2XX.get() + "\n" +
                    "# HELP http_requests_4xx_total Total 4xx responses\n" +
                    "# TYPE http_requests_4xx_total counter\n" +
                    "http_requests_4xx_total " + HTTP_REQUESTS_4XX.get() + "\n" +
                    "# HELP http_requests_5xx_total Total 5xx responses\n" +
                    "# TYPE http_requests_5xx_total counter\n" +
                    "http_requests_5xx_total " + HTTP_REQUESTS_5XX.get() + "\n" +
                    "# HELP process_start_time_seconds Start time of the process since unix epoch\n" +
                    "# TYPE process_start_time_seconds gauge\n" +
                    "process_start_time_seconds " + START_TIME_EPOCH_SECONDS + "\n";

            // Prometheus default content-type for text exposition format
            sendPlain(exchange, 200, metrics, "text/plain; version=0.0.4; charset=utf-8");
            count2xx();
        } catch (Exception e) {
            count5xx();
            sendPlain(exchange, 500, "Internal Server Error\n", "text/plain; charset=utf-8");
        } finally {
            exchange.close();
        }
    }

    // ----------------- Logic -----------------

    private static String analyzeSentiment(String text) {
        if (text == null || text.isBlank()) return "neutral";

        int positiveCount = 0;
        int negativeCount = 0;

        for (String word : POSITIVE_WORDS) {
            if (text.contains(word)) positiveCount++;
        }
        for (String word : NEGATIVE_WORDS) {
            if (text.contains(word)) negativeCount++;
        }

        if (positiveCount > negativeCount) return "positive";
        if (negativeCount > positiveCount) return "negative";
        return "neutral";
    }

    // ----------------- Helpers -----------------

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Cache-Control", "no-store");
        sendBytes(exchange, code, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendPlain(HttpExchange exchange, int code, String text, String contentType) throws IOException {
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", contentType);
        h.set("Cache-Control", "no-store");
        sendBytes(exchange, code, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int code, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return map;

        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = urlDecode(pair.substring(0, eq));
            String val = urlDecode(pair.substring(eq + 1));
            map.put(key, val);
        }
        return map;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    /**
     * Very small JSON parser for {"text":"..."} without external libs.
     * Accepts whitespace. If "text" key missing, returns "".
     */
    private static String extractTextFromJson(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";

        // naive but safe-ish for a learning project:
        // find "text" : "..."
        int idx = trimmed.indexOf("\"text\"");
        if (idx < 0) return "";
        int colon = trimmed.indexOf(':', idx);
        if (colon < 0) throw new IllegalArgumentException("bad json");
        int firstQuote = trimmed.indexOf('"', colon + 1);
        if (firstQuote < 0) return "";
        int secondQuote = findJsonStringEnd(trimmed, firstQuote + 1);
        if (secondQuote < 0) throw new IllegalArgumentException("bad json");

        String raw = trimmed.substring(firstQuote + 1, secondQuote);
        return jsonUnescape(raw);
    }

    // finds ending quote handling escaped quotes \" and escaped backslashes
    private static int findJsonStringEnd(String s, int start) {
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        // minimal escape for JSON strings
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String jsonUnescape(String s) {
        if (s == null) return "";
        // minimal unescape for the few sequences we escape
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static void countRequest() {
        HTTP_REQUESTS_TOTAL.incrementAndGet();
    }

    private static void count2xx() {
        HTTP_REQUESTS_2XX.incrementAndGet();
    }

    private static void count4xx() {
        HTTP_REQUESTS_4XX.incrementAndGet();
    }

    private static void count5xx() {
        HTTP_REQUESTS_5XX.incrementAndGet();
    }
}
