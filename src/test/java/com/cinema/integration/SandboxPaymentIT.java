package com.cinema.integration;

import com.cinema.feature.payment.*;
import com.cinema.shared.config.VnpayConfig;
import com.cinema.shared.validation.BusinessRuleException;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Signed provider messages are simulated; all HTTP/JDBC/transactions are real. */
class SandboxPaymentIT {
    private final BusinessRulesIT fixture=new BusinessRulesIT();
    private final VnpayConfig config=new VnpayConfig("TEST1234","local-test-secret",URI.create("http://127.0.0.1:8889/sandbox/return"),0);
    private final VnpayGateway gateway=new VnpayGateway(config);
    private SandboxPaymentService service;
    private VnpayCallbackServer server;
    private String reference;
    private int bookingId;

    @BeforeAll static void init(){BusinessRulesIT.init();}
    @BeforeEach void setup() throws Exception {
        fixture.setup();
        service=new SandboxPaymentService(new SandboxPaymentRepository(BusinessRulesIT.cp),BusinessRulesIT.bookings,BusinessRulesIT.tx,config,Clock.systemUTC());
        server=new VnpayCallbackServer(service,0);
        var checkout=service.create(fixture.screening,null,fixture.employee,List.of(fixture.standardSeat));
        bookingId=checkout.bookingId();reference=gateway.verify(checkout.url().getRawQuery()).get("vnp_TxnRef");
    }
    @AfterEach void cleanup(){if(server!=null)server.close();fixture.cleanup();}

    private Map<String,String> message(String response,String status){
        return new TreeMap<>(Map.of("vnp_TmnCode","TEST1234","vnp_TxnRef",reference,"vnp_Amount","10000000",
                "vnp_ResponseCode",response,"vnp_TransactionStatus",status,"vnp_TransactionNo","123456789"));
    }
    private String signed(Map<String,String> p){return VnpayGateway.canonical(p)+"&vnp_SecureHash="+gateway.signature(p);}
    private String get(String path,String query) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path+"?"+query)).timeout(java.time.Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
    }
    private String ipn(Map<String,String> p)throws Exception{return get("/sandbox/ipn",signed(p));}
    private void pending()throws Exception{
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'",bookingId));
        assertEquals(0,fixture.revenue().totalPaidRevenue().signum());
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?",fixture.screening));
    }
    @Test void validIpnConfirmsAndRepeatedIpnIsIdempotent()throws Exception{
        assertTrue(ipn(message("00","00")).contains("\"00\""));
        assertTrue(ipn(message("00","00")).contains("\"02\""));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'",bookingId));
        assertEquals(0,new java.math.BigDecimal("100000").compareTo(fixture.revenue().totalPaidRevenue()));
        var conflicting=message("00","00");conflicting.put("vnp_TransactionNo","987654321");
        assertTrue(ipn(conflicting).contains("\"99\""));
    }
    @Test void manualServiceCannotConfirmOrFailGatewayPendingPayment() throws Exception {
        for (String target : List.of("SUCCESS", "FAILED")) {
            assertThrows(BusinessRuleException.class,
                    () -> BusinessRulesIT.booking.transitionPendingPayment(bookingId, target));
            pending();
        }
        assertTrue(ipn(message("00", "00")).contains("\"00\""));
        assertEquals(1, BusinessRulesIT.scalar("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'", bookingId));
    }
    @Test void checkoutAndExpiryShareVietnamTimeFromInjectedClock() throws Exception {
        var instant = java.time.Instant.parse("2026-11-15T23:58:00Z");
        var fixed = new SandboxPaymentService(new SandboxPaymentRepository(BusinessRulesIT.cp),
                BusinessRulesIT.bookings, BusinessRulesIT.tx, config, Clock.fixed(instant, java.time.ZoneOffset.UTC));
        var checkout = fixed.create(fixture.screening, null, fixture.employee, List.of(fixture.vipSeat));
        var data = gateway.verify(checkout.url().getRawQuery());
        assertEquals("20261116065800", data.get("vnp_CreateDate"));
        assertEquals("20261116071300", data.get("vnp_ExpireDate"));
        fixed.expirePending();
        assertEquals(1, BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", checkout.bookingId()));
    }
    @Test void returnUrlNeverConfirmsEvenWithValidSuccessSignature()throws Exception{
        get("/sandbox/return",signed(message("00","00")));pending();
    }
    @Test void tamperedCallbackLeavesAllStatePending()throws Exception{
        assertTrue(get("/sandbox/ipn",signed(message("00","00")).replace("10000000","10000001")).contains("\"97\""));pending();
    }
    @Test void correctlySignedWrongAmountIsRejected()throws Exception{
        var p=message("00","00");p.put("vnp_Amount","9999900");assertTrue(ipn(p).contains("\"04\""));pending();
    }
    @Test void unknownOrderAndManualPaymentCannotBeConfirmed()throws Exception{
        var p=message("00","00");p.put("vnp_TxnRef","VP2147483647P2147483647");assertTrue(ipn(p).contains("\"01\""));pending();
        int manual=fixture.buy("PENDING",List.of(fixture.vipSeat));
        long paymentId=BusinessRulesIT.scalar("SELECT payment_id FROM payment WHERE booking_id=?",manual);
        p=message("00","00");p.put("vnp_TxnRef","VP"+manual+"P"+paymentId);p.put("vnp_Amount","12000000");
        assertTrue(ipn(p).contains("\"01\""));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'",manual));
    }
    @Test void failedStatusDespiteResponse00CancelsAndReleasesSeats()throws Exception{
        assertTrue(ipn(message("00","02")).contains("\"00\""));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CANCELLED'",bookingId));
        assertEquals(0,BusinessRulesIT.scalar("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?",fixture.screening));
        assertEquals(0,fixture.revenue().totalPaidRevenue().signum());
    }
    @Test void duplicateKeysAndWrongMerchantCannotMutateOrder()throws Exception{
        assertTrue(get("/sandbox/ipn",signed(message("00","00"))+"&vnp_Amount=1").contains("\"97\""));
        var p=message("00","00");p.put("vnp_TmnCode","OTHER123");assertTrue(ipn(p).contains("\"97\""));pending();
    }
    @Test void expiryReleasesOnlyGatewayPendingOrder()throws Exception{
        int manual=fixture.buy("PENDING",List.of(fixture.vipSeat));
        BusinessRulesIT.sql("UPDATE booking SET booked_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE booking_id IN (?,?)",bookingId,manual);
        service.expirePending();
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CANCELLED'",bookingId));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'",manual));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?",fixture.screening));
    }
    @Test void lateSuccessAfterExpiryDoesNotTakeResoldSeatAndLeavesAuditMarker()throws Exception{
        BusinessRulesIT.sql("UPDATE booking SET booked_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE booking_id=?",bookingId);service.expirePending();
        int resold=fixture.buy("SUCCESS",List.of(fixture.standardSeat));
        assertTrue(ipn(message("00","00")).contains("\"00\""));
        assertTrue(ipn(message("00","00")).contains("\"02\""));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM payment WHERE booking_id=? AND transaction_ref='VNPAY-LATE-123456789' AND payment_status='FAILED'",bookingId));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CONFIRMED'",resold));
        assertEquals(0,new java.math.BigDecimal("100000").compareTo(fixture.revenue().totalPaidRevenue()));
    }
    @Test void paidGatewayOrderCannotPretendRefundThroughCashCancellation()throws Exception{
        ipn(message("00","00"));
        assertThrows(BusinessRuleException.class,()->BusinessRulesIT.booking.cancelBooking(bookingId));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'",bookingId));
        try(var c=BusinessRulesIT.cp.getConnection()){
            assertThrows(java.sql.SQLException.class,()->BusinessRulesIT.exec(c,"UPDATE booking SET booking_status='CANCELLED' WHERE booking_id=?",bookingId));
        }
    }
    @Test void concurrentIpnOnlyConfirmsOnce()throws Exception{
        try(var pool=Executors.newFixedThreadPool(4)){
            List<Future<String>> futures=new ArrayList<>();var gate=new CountDownLatch(1);
            for(int i=0;i<4;i++)futures.add(pool.submit(()->{gate.await();return ipn(message("00","00"));}));
            gate.countDown();int first=0,repeated=0;
            for(var f:futures){String result=f.get(15,TimeUnit.SECONDS);if(result.contains("\"00\""))first++;else if(result.contains("\"02\""))repeated++;else fail(result);}
            assertEquals(1,first);assertEquals(3,repeated);
        }
    }
    @Test void missingCredentialsNeverCreatesBooking()throws Exception{
        var absent=new SandboxPaymentService(new SandboxPaymentRepository(BusinessRulesIT.cp),BusinessRulesIT.bookings,BusinessRulesIT.tx,
                new VnpayConfig("","",config.returnUrl(),0),Clock.systemUTC());
        long before=BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?",fixture.screening);
        assertThrows(IllegalStateException.class,()->absent.create(fixture.screening,null,fixture.employee,List.of(fixture.vipSeat)));
        assertEquals(before,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?",fixture.screening));
    }
    @Test void sandboxCheckoutForStartedScreeningDoesNotCreateAnotherOrder()throws Exception{
        BusinessRulesIT.sql("UPDATE screening SET start_time=DATE_SUB(NOW(),INTERVAL 4 HOUR),end_time=NOW() WHERE screening_id=?",fixture.screening);
        assertThrows(BusinessRuleException.class,()->service.create(fixture.screening,null,fixture.employee,List.of(fixture.vipSeat)));
        assertEquals(1,BusinessRulesIT.scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?",fixture.screening));
    }
    @Test void unfinishedOrContradictoryProviderStateWaitsForRetry()throws Exception{
        for(String status:List.of("01","04","05","99"))assertTrue(ipn(message("00",status)).contains("\"99\""));
        assertTrue(ipn(message("24","00")).contains("\"99\""));pending();
    }
    @Test void runtimeGuardRejectsDatabaseOutsideTestSchema(){
        var current=BusinessRulesIT.cp.config();
        var other=new com.cinema.shared.db.ConnectionProvider(new com.cinema.shared.config.AppConfig(
                current.jdbcUrl().replace("/cinema_chain_test","/information_schema"),current.username(),current.password()));
        assertThrows(SecurityException.class,()->new SandboxPaymentRepository(other).assertTestDatabase());
    }
}
