package com.cinema.unit;

import com.cinema.feature.payment.SandboxPaymentService;
import com.cinema.feature.payment.SepayGateway;
import com.cinema.feature.payment.SepayJsonParser;
import com.cinema.feature.payment.SepayWebhookEvent;
import com.cinema.feature.payment.SepayWebhookServer;
import com.cinema.shared.config.SepayConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SepayGatewayTest {

    private static final String SAMPLE_BODY = "{\"id\":90001,\"gateway\":\"MB\",\"transactionDate\":\"2026-11-22 22:00:00\","
            + "\"accountNumber\":\"0123456789\",\"subAccount\":null,\"code\":\"SP1P2\",\"content\":\"SP1P2 thanh toan ve\","
            + "\"transferType\":\"in\",\"transferAmount\":100000,\"accumulated\":2500000,\"referenceCode\":\"FT26326ABC\",\"description\":\"\"}";
    private static final long SAMPLE_TS = 1763823600L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochSecond(SAMPLE_TS + 30), ZoneOffset.UTC);

    private final SepayConfig hmacConfig = new SepayConfig(
            "MB", "0123456789", "CINEVIET SANDBOX",
            SepayConfig.AuthMode.HMAC_SHA256, "sepay-sandbox-secret-2026", 0);
    private final SepayGateway hmacGateway = new SepayGateway(hmacConfig);

    @Test
    void hmacSha256MatchesIndependentPythonStdlibVectorAndAcceptsWithinReplayWindow() {
        byte[] raw = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        String sig = hmacGateway.signHmac(SAMPLE_TS, raw);
        assertEquals("sha256=5c34f0f84b32497e0f409fbe50336e04d34785bf7d40cfd7c4de6d193e0b86a4", sig);

        Map<String, List<String>> headers = Map.of(
                "X-SePay-Signature", List.of(sig),
                "X-SePay-Timestamp", List.of(Long.toString(SAMPLE_TS))
        );
        assertDoesNotThrow(() -> hmacGateway.verifyAuth(headers, raw, FIXED_CLOCK));
    }

    @Test
    void hmacSha256RejectsExpiredFutureOrMalformedTimestamp() {
        byte[] raw = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        long expiredTs = SAMPLE_TS - 400;
        long futureTs = SAMPLE_TS + 400;

        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of(hmacGateway.signHmac(expiredTs, raw)),
                "X-SePay-Timestamp", List.of(Long.toString(expiredTs))
        ), raw, FIXED_CLOCK));

        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of(hmacGateway.signHmac(futureTs, raw)),
                "X-SePay-Timestamp", List.of(Long.toString(futureTs))
        ), raw, FIXED_CLOCK));

        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of("sha256=" + "a".repeat(64)),
                "X-SePay-Timestamp", List.of("not-a-timestamp")
        ), raw, FIXED_CLOCK));
    }

    @Test
    void hmacSha256RejectsTamperedBodyBadHexAndDuplicateHeaders() {
        byte[] raw = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        byte[] tampered = SAMPLE_BODY.replace("100000", "200000").getBytes(StandardCharsets.UTF_8);
        String sig = hmacGateway.signHmac(SAMPLE_TS, raw);

        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of(sig),
                "X-SePay-Timestamp", List.of(Long.toString(SAMPLE_TS))
        ), tampered, FIXED_CLOCK));

        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of("sha256=xyz"),
                "X-SePay-Timestamp", List.of(Long.toString(SAMPLE_TS))
        ), raw, FIXED_CLOCK));

        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of(sig, sig),
                "X-SePay-Timestamp", List.of(Long.toString(SAMPLE_TS))
        ), raw, FIXED_CLOCK));
    }

    @Test
    void apiKeyModeVerifiesConstantTimeAndRejectsWrongMissingOrDuplicateHeader() {
        SepayConfig apiKeyConfig = new SepayConfig(
                "MB", "0123456789", "CINEVIET SANDBOX",
                SepayConfig.AuthMode.API_KEY, "sepay-sandbox-apikey-2026", 0);
        SepayGateway apiKeyGateway = new SepayGateway(apiKeyConfig);
        byte[] raw = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> apiKeyGateway.verifyAuth(
                Map.of("authorization", List.of("Apikey sepay-sandbox-apikey-2026")), raw, FIXED_CLOCK));

        assertThrows(SecurityException.class, () -> apiKeyGateway.verifyAuth(
                Map.of("Authorization", List.of("Apikey wrong-secret-value-2026")), raw, FIXED_CLOCK));
        assertThrows(SecurityException.class, () -> apiKeyGateway.verifyAuth(
                Map.of("Authorization", List.of("Bearer sepay-sandbox-apikey-2026")), raw, FIXED_CLOCK));
        assertThrows(SecurityException.class, () -> apiKeyGateway.verifyAuth(
                Map.of("Authorization", List.of("Apikey sepay-sandbox-apikey-2026", "Apikey sepay-sandbox-apikey-2026")),
                raw, FIXED_CLOCK));
    }

    @Test
    void noDowngradeBetweenHmacAndApiKeyModes() {
        byte[] raw = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);

        // Configured for HMAC_SHA256 -> sending valid Apikey header must be rejected
        assertThrows(SecurityException.class, () -> hmacGateway.verifyAuth(
                Map.of("Authorization", List.of("Apikey sepay-sandbox-secret-2026")), raw, FIXED_CLOCK));

        // Configured for API_KEY -> sending valid HMAC signature without Apikey header must be rejected
        SepayConfig apiKeyConfig = new SepayConfig(
                "MB", "0123456789", "CINEVIET SANDBOX",
                SepayConfig.AuthMode.API_KEY, "sepay-sandbox-secret-2026", 0);
        SepayGateway apiKeyGateway = new SepayGateway(apiKeyConfig);
        assertThrows(SecurityException.class, () -> apiKeyGateway.verifyAuth(Map.of(
                "X-SePay-Signature", List.of(hmacGateway.signHmac(SAMPLE_TS, raw)),
                "X-SePay-Timestamp", List.of(Long.toString(SAMPLE_TS))
        ), raw, FIXED_CLOCK));
    }

    @Test
    void jsonParserParsesValidSepayPayloadWithNullsEscapesAndUnicode() {
        String json = """
                {
                  "id": 90001,
                  "gateway": "MB",
                  "transactionDate": "2026-11-22 22:00:00",
                  "accountNumber": "0123456789",
                  "subAccount": null,
                  "code": "SP12P34",
                  "content": "Thanh to\\u00e1n v\\u00e9 \\"SP12P34\\"",
                  "transferType": "in",
                  "transferAmount": 190000,
                  "accumulated": 2500000,
                  "referenceCode": "FT26326ABC",
                  "description": "Bank SMS"
                }
                """;
        SepayWebhookEvent event = SepayJsonParser.parseWebhookEvent(json);
        assertEquals(90001L, event.id());
        assertEquals("MB", event.gateway());
        assertEquals("0123456789", event.accountNumber());
        assertNull(event.subAccount());
        assertEquals("SP12P34", event.code());
        assertEquals("Thanh toán vé \"SP12P34\"", event.content());
        assertEquals("in", event.transferType());
        assertEquals(0, new BigDecimal("190000").compareTo(event.transferAmount()));
    }

    @Test
    void jsonParserRejectsDuplicateKeysNestedObjectsTrailingDataAndOversizedBody() {
        String duplicateKey = SAMPLE_BODY.replace("\"transferAmount\":100000",
                "\"transferAmount\":100000,\"transferAmount\":1");
        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent(duplicateKey));

        String nestedObject = "{\"id\":1,\"nested\":{\"x\":1}}";
        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent(nestedObject));

        String trailingGarbage = SAMPLE_BODY + " extra";
        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent(trailingGarbage));

        String fractionalAmount = SAMPLE_BODY.replace("\"transferAmount\":100000", "\"transferAmount\":100000.50");
        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent(fractionalAmount));

        String hugeExponent = SAMPLE_BODY.replace("\"transferAmount\":100000", "\"transferAmount\":1e99999999");
        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent(hugeExponent));

        String nulCharEscape = SAMPLE_BODY.replace("\"MB\"", "\"\\u0000\"");
        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent(nulCharEscape));

        assertThrows(IllegalArgumentException.class, () -> SepayJsonParser.parseWebhookEvent("a".repeat(16385)));
    }

    @Test
    void vndAmountRequiresPositiveExactIntegerAndRejectsFractionsOrOverflow() {
        assertEquals("120000", SepayGateway.vndAmount(new BigDecimal("120000.00")));
        assertThrows(IllegalArgumentException.class, () -> SepayGateway.vndAmount(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SepayGateway.vndAmount(new BigDecimal("-1000")));
        assertThrows(ArithmeticException.class, () -> SepayGateway.vndAmount(new BigDecimal("1000.50")));
        assertThrows(IllegalArgumentException.class, () -> SepayGateway.vndAmount(new BigDecimal("1000000000000")));
        assertThrows(IllegalArgumentException.class, () -> SepayGateway.vndAmount(new BigDecimal("1e99999999")));
    }

    @Test
    void qrImageUriUsesOfficialSepayHttpsHostCompactTemplateAndEncodedParams() {
        URI uri = hmacGateway.qrImageUri("SP15P28", new BigDecimal("120000.00"));
        assertEquals("https", uri.getScheme());
        assertEquals("qr.sepay.vn", uri.getHost());
        assertEquals("/img", uri.getPath());
        String q = uri.getRawQuery();
        assertTrue(q.contains("acc=0123456789"));
        assertTrue(q.contains("bank=MB"));
        assertTrue(q.contains("amount=120000"));
        assertTrue(q.contains("des=SP15P28"));
        assertTrue(q.contains("template=compact"));
    }

    @Test
    void extractReferenceFindsEmbeddedBankNarrativeCodeAndRejectsAmbiguousMultipleCodes() {
        Optional<SepayGateway.ReferenceToken> fromNarrative = SepayGateway.extractReference(
                null, "MBVCB.12345.sp42p99.CT tu 0987654321");
        assertTrue(fromNarrative.isPresent());
        assertEquals(new SepayGateway.ReferenceToken(42, 99), fromNarrative.get());

        Optional<SepayGateway.ReferenceToken> matchingBoth = SepayGateway.extractReference(
                "SP42P99", "Chuyen khoan SP42P99 ve xem phim");
        assertEquals(Optional.of(new SepayGateway.ReferenceToken(42, 99)), matchingBoth);

        assertEquals(Optional.empty(), SepayGateway.extractReference(null, "Chuyen khoan khong co ma don"));

        assertThrows(IllegalStateException.class, () ->
                SepayGateway.extractReference("SP1P2", "Noi dung chua ma khac SP3P4"));
        assertThrows(IllegalStateException.class, () ->
                SepayGateway.extractReference(null, "Hai ma SP1P2 va SP3P4 trong cung noi dung"));
    }

    @Test
    void missingCredentialsOrInvalidConfigFailFastAndToStringMasksSecret() {
        SepayConfig empty = new SepayConfig("", "", "CINEVIET", SepayConfig.AuthMode.HMAC_SHA256, "", 0);
        assertThrows(IllegalStateException.class, empty::requireCredentials);
        assertThrows(IllegalArgumentException.class, () -> SepayConfig.AuthMode.parse("NONE"));
        assertThrows(IllegalArgumentException.class, () ->
                new SepayConfig("MB", "0123456789", "CINEVIET", SepayConfig.AuthMode.HMAC_SHA256, "secret-1234567890", 70000));
        assertFalse(hmacConfig.toString().contains(hmacConfig.webhookSecret()));
    }

    @Test
    void qrFetcherRejectsNonSepayHostHttpSchemeOrInvalidPath() {
        SepayGateway.QrImageFetcher fetcher = SepayGateway.defaultQrImageFetcher();
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetchPng(URI.create("http://qr.sepay.vn/img?acc=0123456789&bank=MB")));
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetchPng(URI.create("https://evil.example.com/img?acc=0123456789&bank=MB")));
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetchPng(URI.create("https://qr.sepay.vn:8443/img?acc=0123456789&bank=MB")));
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetchPng(URI.create("https://qr.sepay.vn/other")));
    }

    @Test
    void loopbackWebhookServerEnforcesMethodPathContentTypeBodyLimitAndNoStoreHeaders() throws Exception {
        try (SepayWebhookServer srv = new SepayWebhookServer(
                (headers, body) -> new SandboxPaymentService.WebhookResult(200, true, "CONFIRMED", "OK"),
                () -> {},
                0
        )) {
            HttpClient client = HttpClient.newHttpClient();
            URI validUri = URI.create("http://127.0.0.1:" + srv.port() + SepayWebhookServer.WEBHOOK_PATH);

            HttpResponse<String> getResp = client.send(
                    HttpRequest.newBuilder(validUri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, getResp.statusCode());

            HttpResponse<String> badPath = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + srv.port() + "/sandbox/wrong"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, badPath.statusCode());

            HttpResponse<String> badMedia = client.send(
                    HttpRequest.newBuilder(validUri)
                            .header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(415, badMedia.statusCode());

            HttpResponse<String> oversized = client.send(
                    HttpRequest.newBuilder(validUri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("x".repeat(17_000))).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(413, oversized.statusCode());

            HttpResponse<String> ok = client.send(
                    HttpRequest.newBuilder(validUri)
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(SAMPLE_BODY)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ok.statusCode());
            assertTrue(ok.body().contains("\"CONFIRMED\""));
            assertEquals("no-store", ok.headers().firstValue("Cache-Control").orElse(""));
        }
    }
}
