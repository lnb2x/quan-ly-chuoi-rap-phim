package com.cinema.feature.payment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Máy chủ HTTP cục bộ (chỉ lắng nghe {@code 127.0.0.1}) nhận Webhook JSON từ SePay Test Mode / SePay CLI.
 * Khi kết nối từ Dashboard SePay qua mạng công cộng, người vận hành cấu hình SePay CLI hoặc HTTPS forward về đường dẫn này.
 */
public final class SepayWebhookServer implements AutoCloseable {

    @FunctionalInterface
    public interface WebhookHandler {
        SandboxPaymentService.WebhookResult handle(Map<String, List<String>> headers, byte[] rawBody);
    }

    public static final String WEBHOOK_PATH = "/sandbox/sepay/webhook";

    private final HttpServer server;
    private final WebhookHandler handler;
    private final ExecutorService requests = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sepay-webhook");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sepay-expiry");
        t.setDaemon(true);
        return t;
    });

    public SepayWebhookServer(SandboxPaymentService service, int port) throws IOException {
        this(service::webhook, service::expirePending, port);
    }

    public SepayWebhookServer(WebhookHandler handler, Runnable expiryTask, int port) throws IOException {
        this.handler = handler;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        this.server.setExecutor(requests);
        this.server.createContext("/sandbox", this::handleExchange);
        this.expiry.scheduleWithFixedDelay(() -> {
            try {
                expiryTask.run();
            } catch (RuntimeException ex) {
                System.err.println("[SEPAY-SANDBOX] Chưa giải phóng được đơn hết hạn; sẽ thử lại.");
            }
        }, 0, 30, TimeUnit.SECONDS);
        this.server.start();
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (!WEBHOOK_PATH.equals(path)) {
                writeJson(exchange, 404, false, "NOT_FOUND", "Đường dẫn Webhook không tồn tại.");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, false, "METHOD_NOT_ALLOWED", "Webhook SePay chỉ chấp nhận phương thức POST.");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.trim().toLowerCase(Locale.ROOT).startsWith("application/json")) {
                writeJson(exchange, 415, false, "UNSUPPORTED_MEDIA_TYPE", "Yêu cầu Content-Type là application/json.");
                return;
            }

            byte[] rawBody;
            try (InputStream in = exchange.getRequestBody()) {
                rawBody = in.readNBytes(SepayGateway.MAX_WEBHOOK_BYTES + 1);
            }
            if (rawBody.length > SepayGateway.MAX_WEBHOOK_BYTES) {
                writeJson(exchange, 413, false, "PAYLOAD_TOO_LARGE", "Payload Webhook vượt giới hạn 16KB.");
                return;
            }

            SandboxPaymentService.WebhookResult result;
            try {
                result = handler.handle(exchange.getRequestHeaders(), rawBody);
            } catch (RuntimeException ex) {
                System.err.println("[SEPAY-SANDBOX] Lỗi xử lý Webhook; SePay có thể gửi lại để đối soát.");
                writeJson(exchange, 500, false, "INTERNAL_ERROR", "Chưa xử lý được giao dịch; cần thử lại.");
                return;
            }
            writeJson(exchange, result.httpStatus(), result.success(), result.code(), result.message());
        } finally {
            exchange.close();
        }
    }

    private static void writeJson(HttpExchange exchange, int status, boolean success, String code, String message)
            throws IOException {
        String body = "{\"success\":" + success
                + ",\"code\":\"" + escapeJson(code) + "\""
                + ",\"message\":\"" + escapeJson(message) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\r' -> sb.append("\\r");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        requests.shutdownNow();
        expiry.shutdownNow();
    }
}
