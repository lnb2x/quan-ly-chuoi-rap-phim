package com.cinema.feature.payment;

import com.cinema.shared.config.SepayConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cổng giao thức SePay Sandbox (VietQR động {@code qr.sepay.vn} và xác thực Webhook JSON).
 * Đảm bảo so sánh mật mã thời gian hằng, cửa sổ chống phát lại 300 giây và không cho phép hạ cấp chế độ xác thực.
 */
public final class SepayGateway {

    public interface QrImageFetcher {
        byte[] fetchPng(URI qrUri) throws IOException;
    }

    public record ReferenceToken(int bookingId, int paymentId) {
    }

    public static final int MAX_WEBHOOK_BYTES = 16_384;
    public static final long REPLAY_WINDOW_SECONDS = 300L;
    private static final int MAX_QR_IMAGE_BYTES = 1_048_576;
    private static final BigInteger MAX_VND_AMOUNT = BigInteger.valueOf(999_999_999_999L);
    private static final BigDecimal MAX_VND_DECIMAL = new BigDecimal(MAX_VND_AMOUNT);
    private static final Pattern STRICT_CODE = Pattern.compile("^SP([1-9][0-9]{0,8})P([1-9][0-9]{0,8})$");
    private static final Pattern EMBEDDED_CODE = Pattern.compile(
            "(?i)(?<![A-Z0-9])SP([1-9][0-9]{0,8})P([1-9][0-9]{0,8})(?![A-Z0-9])");

    private final SepayConfig config;

    public SepayGateway(SepayConfig config) {
        this.config = config;
    }

    public URI qrImageUri(String transferCode, BigDecimal amount) {
        config.requireCredentials();
        if (transferCode == null || !STRICT_CODE.matcher(transferCode).matches()) {
            throw new IllegalArgumentException("Mã nội dung chuyển khoản SePay không hợp lệ.");
        }
        String vnd = vndAmount(amount);
        String query = "acc=" + encode(config.accountNumber())
                + "&bank=" + encode(config.bankCode())
                + "&amount=" + encode(vnd)
                + "&des=" + encode(transferCode)
                + "&template=compact";
        return URI.create("https://qr.sepay.vn/img?" + query);
    }

    public String signHmac(long epochSeconds, byte[] rawBody) {
        config.requireCredentials();
        if (rawBody == null) {
            throw new IllegalArgumentException("Nội dung ký không được phép null.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] prefix = (epochSeconds + ".").getBytes(StandardCharsets.UTF_8);
            mac.update(prefix);
            byte[] digest = mac.doFinal(rawBody);
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể tạo chữ ký HMAC-SHA256 cho SePay.", ex);
        }
    }

    public void verifyAuth(Map<String, List<String>> headers, byte[] rawBody, Clock clock) {
        config.requireCredentials();
        if (headers == null || rawBody == null || rawBody.length == 0 || rawBody.length > MAX_WEBHOOK_BYTES) {
            throw new SecurityException("Yêu cầu Webhook thiếu dữ liệu hoặc vượt kích thước cho phép.");
        }
        if (config.authMode() == SepayConfig.AuthMode.HMAC_SHA256) {
            verifyHmacAuth(headers, rawBody, clock);
        } else {
            verifyApiKeyAuth(headers);
        }
    }

    private void verifyHmacAuth(Map<String, List<String>> headers, byte[] rawBody, Clock clock) {
        String sigHeader = singleHeader(headers, "X-SePay-Signature");
        String tsHeader = singleHeader(headers, "X-SePay-Timestamp");
        if (sigHeader == null || tsHeader == null || !tsHeader.matches("[1-9][0-9]{8,11}")) {
            throw new SecurityException("Thiếu hoặc sai định dạng header X-SePay-Signature / X-SePay-Timestamp.");
        }
        if (!sigHeader.regionMatches(false, 0, "sha256=", 0, 7)) {
            throw new SecurityException("Tiền tố chữ ký X-SePay-Signature không hợp lệ.");
        }
        String hexPart = sigHeader.substring(7);
        if (!hexPart.matches("[a-fA-F0-9]{64}")) {
            throw new SecurityException("Giá trị hex của chữ ký HMAC-SHA256 không hợp lệ.");
        }
        long timestamp = Long.parseLong(tsHeader);
        long nowSeconds = clock.instant().getEpochSecond();
        if (Math.abs(nowSeconds - timestamp) > REPLAY_WINDOW_SECONDS) {
            throw new SecurityException("Dấu thời gian Webhook nằm ngoài cửa sổ chống phát lại 300 giây.");
        }
        byte[] provided = HexFormat.of().parseHex(hexPart);
        byte[] expected = HexFormat.of().parseHex(signHmac(timestamp, rawBody).substring(7));
        if (!MessageDigest.isEqual(provided, expected)) {
            throw new SecurityException("Chữ ký HMAC-SHA256 không khớp.");
        }
    }

    private void verifyApiKeyAuth(Map<String, List<String>> headers) {
        String authHeader = singleHeader(headers, "Authorization");
        if (authHeader == null || !authHeader.startsWith("Apikey ")) {
            throw new SecurityException("Thiếu hoặc sai tiền tố Authorization: Apikey.");
        }
        String providedKey = authHeader.substring("Apikey ".length());
        if (providedKey.isEmpty()) {
            throw new SecurityException("API Key SePay rỗng.");
        }
        byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
        byte[] secretBytes = config.webhookSecret().getBytes(StandardCharsets.UTF_8);
        boolean sameHash = MessageDigest.isEqual(sha256(providedBytes), sha256(secretBytes));
        if (!sameHash || providedBytes.length != secretBytes.length) {
            throw new SecurityException("API Key SePay không hợp lệ.");
        }
    }

    public static Optional<ReferenceToken> extractReference(String code, String content) {
        Set<ReferenceToken> tokens = new LinkedHashSet<>();
        collectTokens(code, tokens);
        collectTokens(content, tokens);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        if (tokens.size() > 1) {
            throw new IllegalStateException("Phát hiện nhiều mã thanh toán SePay mâu thuẫn trong cùng giao dịch.");
        }
        return Optional.of(tokens.iterator().next());
    }

    private static void collectTokens(String text, Set<ReferenceToken> sink) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = EMBEDDED_CODE.matcher(text);
        while (matcher.find()) {
            int bookingId = Integer.parseInt(matcher.group(1));
            int paymentId = Integer.parseInt(matcher.group(2));
            sink.add(new ReferenceToken(bookingId, paymentId));
        }
    }

    public static String vndAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        }
        if (amount.scale() < -12 || amount.compareTo(MAX_VND_DECIMAL) > 0) {
            throw new IllegalArgumentException("Số tiền vượt giới hạn giao dịch VietQR.");
        }
        BigInteger exact = amount.toBigIntegerExact();
        return exact.toString();
    }

    public static QrImageFetcher defaultQrImageFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return qrUri -> {
            validateQrUri(qrUri);
            HttpRequest req = HttpRequest.newBuilder(qrUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "image/png,image/*")
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = resp.body()) {
                    if (resp.statusCode() != 200) {
                        throw new IOException("Máy chủ VietQR trả về mã HTTP " + resp.statusCode());
                    }
                    String ct = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    if (!ct.startsWith("image/")) {
                        throw new IOException("Phản hồi từ qr.sepay.vn không phải định dạng ảnh hợp lệ.");
                    }
                    byte[] bytes = in.readNBytes(MAX_QR_IMAGE_BYTES + 1);
                    if (bytes.length == 0 || bytes.length > MAX_QR_IMAGE_BYTES) {
                        throw new IOException("Kích thước ảnh QR không hợp lệ hoặc vượt giới hạn 1MB.");
                    }
                    return bytes;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Tải ảnh VietQR bị gián đoạn.", ex);
            }
        };
    }

    public static void validateQrUri(URI qrUri) {
        if (qrUri == null
                || !"https".equalsIgnoreCase(qrUri.getScheme())
                || !"qr.sepay.vn".equalsIgnoreCase(qrUri.getHost())
                || (qrUri.getPort() != -1 && qrUri.getPort() != 443)
                || !"/img".equals(qrUri.getRawPath())
                || qrUri.getUserInfo() != null
                || qrUri.getFragment() != null) {
            throw new IllegalArgumentException("Chỉ cho phép tải ảnh VietQR qua HTTPS chính thức https://qr.sepay.vn/img.");
        }
    }

    private static String singleHeader(Map<String, List<String>> headers, String targetName) {
        String found = null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(targetName)) {
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    continue;
                }
                if (values.size() > 1 || found != null) {
                    throw new SecurityException("Header bảo mật bị lặp: " + targetName);
                }
                found = values.get(0);
            }
        }
        return found;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Thiếu thuật toán SHA-256.", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
