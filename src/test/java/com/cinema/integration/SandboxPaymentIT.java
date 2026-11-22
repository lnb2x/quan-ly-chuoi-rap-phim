package com.cinema.integration;

import com.cinema.feature.payment.*;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.config.SepayConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.validation.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Signed SePay webhook messages are simulated locally; all HTTP, JDBC, locks, and MySQL 8.4 triggers are real. */
class SandboxPaymentIT {

    private final BusinessRulesIT fixture = new BusinessRulesIT();
    private final SepayConfig config = new SepayConfig(
            "MB", "0123456789", "CINEVIET SANDBOX",
            SepayConfig.AuthMode.HMAC_SHA256, "sepay-sandbox-secret-2026", 0);
    private final SepayGateway gateway = new SepayGateway(config);
    private final HttpClient http = HttpClient.newHttpClient();

    private SandboxPaymentService service;
    private SepayWebhookServer server;
    private SandboxPaymentService.Checkout checkout;
    private int bookingId;

    @BeforeAll
    static void init() {
        BusinessRulesIT.init();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture.setup();
        service = new SandboxPaymentService(
                new SandboxPaymentRepository(BusinessRulesIT.cp),
                BusinessRulesIT.bookings,
                BusinessRulesIT.tx,
                config,
                Clock.systemUTC());
        server = new SepayWebhookServer(service, 0);
        checkout = service.create(fixture.screening, null, fixture.employee, List.of(fixture.standardSeat));
        bookingId = checkout.bookingId();
    }

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.close();
        }
        fixture.cleanup();
    }

    private String jsonPayload(long txId, String acc, String subAcc, String code, String content,
                               String transferType, String amountNumberLiteral) {
        String subJson = subAcc == null ? "null" : "\"" + subAcc + "\"";
        String codeJson = code == null ? "null" : "\"" + code + "\"";
        return "{\"id\":" + txId
                + ",\"gateway\":\"MB\""
                + ",\"transactionDate\":\"2026-11-22 22:00:00\""
                + ",\"accountNumber\":\"" + acc + "\""
                + ",\"subAccount\":" + subJson
                + ",\"code\":" + codeJson
                + ",\"content\":\"" + content + "\""
                + ",\"transferType\":\"" + transferType + "\""
                + ",\"transferAmount\":" + amountNumberLiteral
                + ",\"accumulated\":2500000"
                + ",\"referenceCode\":\"FT" + txId + "\""
                + ",\"description\":\"Sandbox test\"}";
    }

    private String validPayload(long txId) {
        return jsonPayload(txId, "0123456789", null, checkout.transferCode(),
                "Chuyen khoan " + checkout.transferCode(), "in", "100000");
    }

    private HttpResponse<String> postSignedHmac(SepayWebhookServer targetServer, SepayGateway signer,
                                                String rawJson, long epochSeconds) throws Exception {
        byte[] bytes = rawJson.getBytes(StandardCharsets.UTF_8);
        String sig = signer.signHmac(epochSeconds, bytes);
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + targetServer.port() + "/sandbox/sepay/webhook"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-SePay-Signature", sig)
                .header("X-SePay-Timestamp", Long.toString(epochSeconds))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postHmac(String rawJson) throws Exception {
        return postSignedHmac(server, gateway, rawJson, Instant.now().getEpochSecond());
    }

    private void assertPending() throws Exception {
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", bookingId));
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='PENDING' AND payment_method='TRANSFER'", bookingId));
        assertEquals(0, fixture.revenue().totalPaidRevenue().signum());
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", fixture.screening));
    }

    @Test
    void validHmacWebhookConfirmsTransferPaymentAndRepeatedWebhookIsIdempotent() throws Exception {
        HttpResponse<String> first = postHmac(validPayload(900001L));
        assertEquals(200, first.statusCode());
        assertTrue(first.body().contains("\"CONFIRMED\""));

        HttpResponse<String> repeated = postHmac(validPayload(900001L));
        assertEquals(200, repeated.statusCode());
        assertTrue(repeated.body().contains("\"ALREADY_PROCESSED\""));

        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS' AND payment_method='TRANSFER' AND transaction_ref='SEPAY-SUCCESS-900001'",
                bookingId));
        assertEquals(0, new BigDecimal("100000").compareTo(fixture.revenue().totalPaidRevenue()));

        HttpResponse<String> conflicting = postHmac(validPayload(900002L));
        assertEquals(422, conflicting.statusCode());
        assertTrue(conflicting.body().contains("\"CONFLICTING_TRANSACTION\""));
    }

    @Test
    void apiKeyAuthModeWebhookConfirmsOrderAndBlocksHmacHeaderDowngrade() throws Exception {
        SepayConfig apiCfg = new SepayConfig(
                "MB", "0123456789", "CINEVIET SANDBOX",
                SepayConfig.AuthMode.API_KEY, "sepay-apikey-secret-2026", 0);
        SandboxPaymentService apiSvc = new SandboxPaymentService(
                new SandboxPaymentRepository(BusinessRulesIT.cp),
                BusinessRulesIT.bookings, BusinessRulesIT.tx, apiCfg, Clock.systemUTC());
        try (SepayWebhookServer apiSrv = new SepayWebhookServer(apiSvc, apiCfg.port())) {
            var order = apiSvc.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat));
            String body = jsonPayload(900010L, "0123456789", null, order.transferCode(),
                    order.transferCode(), "in", "120000");

            // Sending HMAC headers without Authorization: Apikey must be rejected with 401
            SepayGateway hmacWithSameSecret = new SepayGateway(new SepayConfig(
                    "MB", "0123456789", "CINEVIET", SepayConfig.AuthMode.HMAC_SHA256, "sepay-apikey-secret-2026", 0));
            HttpResponse<String> downgradeAttempt = postSignedHmac(
                    apiSrv, hmacWithSameSecret, body, Instant.now().getEpochSecond());
            assertEquals(401, downgradeAttempt.statusCode());

            // Valid Authorization: Apikey confirms the order
            HttpRequest validReq = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + apiSrv.port() + "/sandbox/sepay/webhook"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Apikey sepay-apikey-secret-2026")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> ok = http.send(validReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ok.statusCode());
            assertEquals(1, BusinessRulesIT.scalar(
                    "SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS' AND transaction_ref='SEPAY-SUCCESS-900010'",
                    order.bookingId()));
        }
    }

    @Test
    void authModeDowngradeFromHmacToApiKeyIsRejectedWith401() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.port() + "/sandbox/sepay/webhook"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Apikey sepay-sandbox-secret-2026")
                .POST(HttpRequest.BodyPublishers.ofString(validPayload(900011L), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
        assertPending();
    }

    @Test
    void expiredOrFutureTimestampOutside300sReplayWindowIsRejectedWith401() throws Exception {
        long now = Instant.now().getEpochSecond();
        assertEquals(401, postSignedHmac(server, gateway, validPayload(900012L), now - 360).statusCode());
        assertEquals(401, postSignedHmac(server, gateway, validPayload(900012L), now + 360).statusCode());
        assertPending();
    }

    @Test
    void tamperedJsonOrDuplicateJsonKeyLeavesBookingPending() throws Exception {
        String raw = validPayload(900013L);
        long now = Instant.now().getEpochSecond();
        String validSig = gateway.signHmac(now, raw.getBytes(StandardCharsets.UTF_8));
        String tampered = raw.replace("100000", "100001");

        HttpRequest tamperedReq = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.port() + "/sandbox/sepay/webhook"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-SePay-Signature", validSig)
                .header("X-SePay-Timestamp", Long.toString(now))
                .POST(HttpRequest.BodyPublishers.ofString(tampered, StandardCharsets.UTF_8))
                .build();
        assertEquals(401, http.send(tamperedReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        String dupKey = raw.replace("\"transferAmount\":100000", "\"transferAmount\":100000,\"transferAmount\":1");
        assertEquals(400, postHmac(dupKey).statusCode());
        assertPending();
    }

    @Test
    void wrongMethodWrongPathWrongContentTypeAndOversizedBodyAreRejected() throws Exception {
        URI webhookUri = URI.create("http://127.0.0.1:" + server.port() + "/sandbox/sepay/webhook");

        HttpResponse<String> getResp = http.send(
                HttpRequest.newBuilder(webhookUri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, getResp.statusCode());

        HttpResponse<String> badPath = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/sandbox/sepay/other"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, badPath.statusCode());

        HttpResponse<String> badCt = http.send(
                HttpRequest.newBuilder(webhookUri)
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(415, badCt.statusCode());

        HttpResponse<String> tooLarge = http.send(
                HttpRequest.newBuilder(webhookUri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("a".repeat(17_000))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(413, tooLarge.statusCode());
        assertPending();
    }

    @Test
    void wrongReceivingAccountLeavesPendingWhileMatchingSubAccountConfirms() throws Exception {
        String wrongAcc = jsonPayload(900020L, "9999999999", "VA8888",
                checkout.transferCode(), checkout.transferCode(), "in", "100000");
        HttpResponse<String> mismatch = postHmac(wrongAcc);
        assertEquals(422, mismatch.statusCode());
        assertTrue(mismatch.body().contains("\"ACCOUNT_MISMATCH\""));
        assertPending();

        // Virtual Account (subAccount) matches configured accountNumber -> confirms
        String subAccMatch = jsonPayload(900021L, "9999999999", "0123456789",
                checkout.transferCode(), checkout.transferCode(), "in", "100000");
        HttpResponse<String> ok = postHmac(subAccMatch);
        assertEquals(200, ok.statusCode());
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'", bookingId));
    }

    @Test
    void outgoingTransferTypeDoesNotConfirmOrCancelPendingBooking() throws Exception {
        String outgoing = jsonPayload(900025L, "0123456789", null,
                checkout.transferCode(), checkout.transferCode(), "out", "100000");
        HttpResponse<String> resp = postHmac(outgoing);
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("\"IGNORED_TRANSFER_TYPE\""));
        assertPending();
    }

    @Test
    void wrongTransferAmountIsRejectedWith422AndLeavesBookingPending() throws Exception {
        String underpaid = jsonPayload(900030L, "0123456789", null,
                checkout.transferCode(), checkout.transferCode(), "in", "90000");
        HttpResponse<String> resp = postHmac(underpaid);
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("\"AMOUNT_MISMATCH\""));
        assertPending();
    }

    @Test
    void unknownOrderManualPaymentAndAmbiguousMultipleCodesCannotBeConfirmed() throws Exception {
        String unknown = jsonPayload(900040L, "0123456789", null,
                "SP99999999P99999999", "SP99999999P99999999", "in", "100000");
        assertEquals(422, postHmac(unknown).statusCode());

        String wrongPaymentId = jsonPayload(900043L, "0123456789", null,
                "SP" + bookingId + "P99999999", "SP" + bookingId + "P99999999", "in", "100000");
        assertEquals(422, postHmac(wrongPaymentId).statusCode());

        int manual = fixture.buy("PENDING", List.of(fixture.vipSeat));
        long manualPaymentId = BusinessRulesIT.scalar("SELECT payment_id FROM payment WHERE booking_id=?", manual);
        String manualCode = "SP" + manual + "P" + manualPaymentId;
        String spoofManual = jsonPayload(900041L, "0123456789", null,
                manualCode, manualCode, "in", "120000");
        assertEquals(422, postHmac(spoofManual).statusCode());
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", manual));

        String ambiguous = jsonPayload(900042L, "0123456789", null,
                checkout.transferCode(), checkout.transferCode() + " SP999P888", "in", "100000");
        HttpResponse<String> ambResp = postHmac(ambiguous);
        assertEquals(422, ambResp.statusCode());
        assertTrue(ambResp.body().contains("\"AMBIGUOUS_REFERENCE\""));
        assertPending();
    }

    @Test
    void sameSepayTxIdCannotConfirmSecondBooking() throws Exception {
        assertEquals(200, postHmac(validPayload(900050L)).statusCode());

        var second = service.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat));
        String replayTxIdOnSecond = jsonPayload(900050L, "0123456789", null,
                second.transferCode(), second.transferCode(), "in", "120000");
        HttpResponse<String> replayResp = postHmac(replayTxIdOnSecond);
        assertEquals(422, replayResp.statusCode());
        assertTrue(replayResp.body().contains("\"CONFLICTING_TRANSACTION\""));
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", second.bookingId()));
    }

    @Test
    void manualServiceCannotConfirmOrFailSepayPendingPayment() throws Exception {
        for (String target : List.of("SUCCESS", "FAILED")) {
            assertThrows(BusinessRuleException.class,
                    () -> BusinessRulesIT.booking.transitionPendingPayment(bookingId, target));
            assertPending();
        }
        assertEquals(200, postHmac(validPayload(900060L)).statusCode());
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'", bookingId));
    }

    @Test
    void checkoutAndExpiryShareVietnamTimeFromInjectedClock() throws Exception {
        Instant instant = Instant.parse("2026-11-15T23:58:00Z");
        SandboxPaymentService fixed = new SandboxPaymentService(
                new SandboxPaymentRepository(BusinessRulesIT.cp),
                BusinessRulesIT.bookings, BusinessRulesIT.tx, config,
                Clock.fixed(instant, ZoneOffset.UTC));
        var order = fixed.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat));
        assertEquals("2026-11-16T06:58", order.createdAt().toString().substring(0, 16));
        assertEquals("2026-11-16T07:13", order.expiresAt().toString().substring(0, 16));
        fixed.expirePending();
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", order.bookingId()));
    }

    @Test
    void expiryReleasesOnlySepayPendingOrderAndPreservesManualPending() throws Exception {
        int manual = fixture.buy("PENDING", List.of(fixture.vipSeat));
        BusinessRulesIT.sql(
                "UPDATE booking SET booked_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE booking_id IN (?,?)",
                bookingId, manual);
        service.expirePending();
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CANCELLED'", bookingId));
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", manual));
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", fixture.screening));
    }

    @Test
    void lateWebhookAfterExpiryDoesNotReclaimResoldSeatAndWritesSepayLateMarker() throws Exception {
        BusinessRulesIT.sql("UPDATE booking SET booked_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE booking_id=?", bookingId);
        service.expirePending();
        int resold = fixture.buy("SUCCESS", List.of(fixture.standardSeat));

        HttpResponse<String> firstLate = postHmac(validPayload(900077L));
        assertEquals(200, firstLate.statusCode());
        assertTrue(firstLate.body().contains("\"LATE_PAYMENT_RECORDED\""));

        HttpResponse<String> repeatedLate = postHmac(validPayload(900077L));
        assertEquals(200, repeatedLate.statusCode());
        assertTrue(repeatedLate.body().contains("\"ALREADY_PROCESSED\""));

        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM payment WHERE booking_id=? AND transaction_ref='SEPAY-LATE-900077' AND payment_status='FAILED'",
                bookingId));
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CONFIRMED'", resold));
        assertEquals(0, new BigDecimal("100000").compareTo(fixture.revenue().totalPaidRevenue()));
    }

    @Test
    void paidSepayOrderCannotPretendRefundThroughServiceOrDirectSqlUpdate() throws Exception {
        assertEquals(200, postHmac(validPayload(900088L)).statusCode());
        assertThrows(BusinessRuleException.class, () -> BusinessRulesIT.booking.cancelBooking(bookingId));
        assertEquals(1, BusinessRulesIT.scalar(
                "SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'", bookingId));
        try (var c = BusinessRulesIT.cp.getConnection()) {
            assertThrows(SQLException.class, () -> BusinessRulesIT.exec(
                    c, "UPDATE booking SET booking_status='CANCELLED' WHERE booking_id=?", bookingId));
            assertThrows(SQLException.class, () -> BusinessRulesIT.exec(
                    c, "UPDATE payment SET payment_status='REFUNDED' WHERE booking_id=?", bookingId));
            assertThrows(SQLException.class, () -> BusinessRulesIT.exec(
                    c, "UPDATE payment SET transaction_ref='TXN-SPOOF' WHERE booking_id=?", bookingId));
        }
    }

    @Test
    void concurrentWebhooksOnlyConfirmOnce() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            CountDownLatch gate = new CountDownLatch(1);
            String payload = validPayload(900099L);
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    return postHmac(payload);
                }));
            }
            gate.countDown();
            int confirmed = 0;
            int duplicate = 0;
            for (var f : futures) {
                HttpResponse<String> resp = f.get(15, TimeUnit.SECONDS);
                assertEquals(200, resp.statusCode());
                if (resp.body().contains("\"CONFIRMED\"")) {
                    confirmed++;
                } else if (resp.body().contains("\"ALREADY_PROCESSED\"")) {
                    duplicate++;
                } else {
                    fail("Phản hồi không mong đợi: " + resp.body());
                }
            }
            assertEquals(1, confirmed);
            assertEquals(3, duplicate);
        }
    }

    @Test
    void concurrentDistinctBookingsAndDuplicateTxIdRaceAreSerializedWithoutDeadlock() throws Exception {
        var second = service.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat));
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            String p1 = jsonPayload(900201L, "0123456789", null, checkout.transferCode(), checkout.transferCode(), "in", "100000");
            String p2 = jsonPayload(900201L, "0123456789", null, second.transferCode(), second.transferCode(), "in", "120000");
            Future<HttpResponse<String>> f1 = pool.submit(() -> { gate.await(); return postHmac(p1); });
            Future<HttpResponse<String>> f2 = pool.submit(() -> { gate.await(); return postHmac(p2); });
            gate.countDown();
            HttpResponse<String> r1 = f1.get(15, TimeUnit.SECONDS);
            HttpResponse<String> r2 = f2.get(15, TimeUnit.SECONDS);
            assertTrue((r1.statusCode() == 200 && r2.statusCode() == 422)
                    || (r1.statusCode() == 422 && r2.statusCode() == 200));
        }
    }

    @Test
    void missingCredentialsStartedScreeningAndNonTestSchemaGuardsRejectSafely() throws Exception {
        SandboxPaymentService absent = new SandboxPaymentService(
                new SandboxPaymentRepository(BusinessRulesIT.cp),
                BusinessRulesIT.bookings, BusinessRulesIT.tx,
                new SepayConfig("", "", "CINEVIET", SepayConfig.AuthMode.HMAC_SHA256, "", 0),
                Clock.systemUTC());
        long before = BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?", fixture.screening);
        assertThrows(IllegalStateException.class,
                () -> absent.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat)));
        assertEquals(before, BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?", fixture.screening));

        BusinessRulesIT.sql(
                "UPDATE screening SET start_time=DATE_SUB(NOW(),INTERVAL 4 HOUR),end_time=NOW() WHERE screening_id=?",
                fixture.screening);
        assertThrows(BusinessRuleException.class,
                () -> service.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat)));
        assertEquals(before, BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?", fixture.screening));

        var current = BusinessRulesIT.cp.config();
        var other = new ConnectionProvider(new AppConfig(
                current.jdbcUrl().replace("/cinema_chain_test", "/information_schema"),
                current.username(), current.password()));
        assertThrows(SecurityException.class, () -> new SandboxPaymentRepository(other).assertTestDatabase());
    }
}
