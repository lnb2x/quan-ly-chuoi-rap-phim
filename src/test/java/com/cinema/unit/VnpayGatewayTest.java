package com.cinema.unit;

import com.cinema.feature.payment.VnpayGateway;
import com.cinema.shared.config.VnpayConfig;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class VnpayGatewayTest {
    private final VnpayConfig config=new VnpayConfig("TEST1234","local-test-secret",URI.create("http://127.0.0.1:8889/sandbox/return"),0);
    private final VnpayGateway gateway=new VnpayGateway(config);
    private Map<String,String> params(){return new TreeMap<>(Map.of("vnp_Amount","10000000","vnp_OrderInfo","Thanh toan ve","vnp_TmnCode","TEST1234","vnp_TxnRef","VP1P2"));}
    private String signed(Map<String,String> p){return VnpayGateway.canonical(p)+"&vnp_SecureHash="+gateway.signature(p);}

    @Test void hmacMatchesIndependentPythonStdlibVector() {
        assertEquals("9e6fc4ee0c4f2443d47e86241f4317b9aef94feeaaefb0bdd6eddcaa743dd57d83cddca2c6a5246241a0a81976935f78f41a892e9744866537fc263e5bc1a70c",gateway.signature(params()));
    }
    @Test void correctSignatureAndEncodedSpacesRoundTrip(){assertEquals(params(),gateway.verify(signed(params())));}
    @Test void changedAmountIsRejected(){assertThrows(IllegalArgumentException.class,()->gateway.verify(signed(params()).replace("10000000","20000000")));}
    @Test void duplicateAndEncodedDuplicateKeysAreRejected(){
        assertThrows(IllegalArgumentException.class,()->gateway.verify(signed(params())+"&vnp_Amount=1"));
        assertThrows(IllegalArgumentException.class,()->gateway.verify(signed(params())+"&%76np_Amount=1"));
    }
    @Test void missingBadOrOversizedSignatureIsRejected(){
        assertThrows(IllegalArgumentException.class,()->gateway.verify(VnpayGateway.canonical(params())));
        assertThrows(IllegalArgumentException.class,()->gateway.verify("vnp_SecureHash=xyz"));
        assertThrows(IllegalArgumentException.class,()->gateway.verify("a".repeat(8193)));
    }
    @Test void wrongMerchantIsRejectedEvenWithValidSignature(){var p=params();p.put("vnp_TmnCode","OTHER123");assertThrows(IllegalArgumentException.class,()->gateway.verify(signed(p)));}
    @Test void moneyUsesExactTwoDecimalUnitsAndRejectsInvalidLimits(){
        assertEquals("12345",VnpayGateway.scaled(new BigDecimal("123.45")));
        assertThrows(IllegalArgumentException.class,()->VnpayGateway.scaled(BigDecimal.ZERO));
        assertThrows(ArithmeticException.class,()->VnpayGateway.scaled(new BigDecimal("0.001")));
        assertThrows(IllegalArgumentException.class,()->VnpayGateway.scaled(new BigDecimal("10000000000")));
    }
    @Test void outgoingUrlIsSandboxSignedAndExpiresAfterFifteenMinutes(){
        URI url=gateway.checkout("VP1P2",new BigDecimal("100000"),LocalDateTime.of(2026,11,13,23,0));
        assertEquals("sandbox.vnpayment.vn",url.getHost());assertEquals("https",url.getScheme());
        var p=gateway.verify(url.getRawQuery());assertEquals("10000000",p.get("vnp_Amount"));
        assertEquals("20261113231500",p.get("vnp_ExpireDate"));
    }
    @Test void missingCredentialsFailBeforeCheckoutAndSecretsAreNotPrinted(){
        var empty=new VnpayConfig("","",config.returnUrl(),0);
        assertThrows(IllegalStateException.class,empty::requireCredentials);
        assertFalse(config.toString().contains(config.secret()));
    }
    @Test void malformedEncodingOrReturnUrlCannotBecomeRedirectInjection(){
        assertThrows(IllegalArgumentException.class,()->VnpayGateway.parse("vnp_Amount=%ZZ"));
        assertThrows(IllegalArgumentException.class,()->new VnpayConfig("TEST1234","local-test-secret",URI.create("javascript:alert(1)"),0));
    }
}
