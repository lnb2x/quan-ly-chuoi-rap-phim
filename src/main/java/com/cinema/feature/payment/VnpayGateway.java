package com.cinema.feature.payment;

import com.cinema.shared.config.VnpayConfig;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Protocol 2.1.0: only the official sandbox URL is permitted. */
public final class VnpayGateway {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayConfig config;
    public VnpayGateway(VnpayConfig config) {this.config=config;}

    public URI checkout(String reference, BigDecimal amount, LocalDateTime created) {
        config.requireCredentials();
        var p=new TreeMap<String,String>();
        p.put("vnp_Version","2.1.0"); p.put("vnp_Command","pay"); p.put("vnp_TmnCode",config.merchant());
        p.put("vnp_Amount",scaled(amount)); p.put("vnp_CurrCode","VND"); p.put("vnp_TxnRef",reference);
        p.put("vnp_OrderInfo","Thanh toan ve "+reference); p.put("vnp_OrderType","other");
        p.put("vnp_Locale","vn"); p.put("vnp_IpAddr","127.0.0.1");
        p.put("vnp_ReturnUrl",config.returnUrl().toString());
        p.put("vnp_CreateDate",TIME.format(created)); p.put("vnp_ExpireDate",TIME.format(created.plusMinutes(15)));
        return URI.create("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"+canonical(p)+"&vnp_SecureHash="+signature(p));
    }

    public Map<String,String> verify(String rawQuery) {
        config.requireCredentials();
        var p=parse(rawQuery);
        String hash=p.remove("vnp_SecureHash"); p.remove("vnp_SecureHashType");
        if (hash==null || !hash.matches("[a-fA-F0-9]{128}")
                || !MessageDigest.isEqual(HexFormat.of().parseHex(hash),HexFormat.of().parseHex(signature(p)))
                || !config.merchant().equals(p.get("vnp_TmnCode"))) {
            throw new IllegalArgumentException("Chữ ký hoặc merchant không hợp lệ.");
        }
        return Map.copyOf(p);
    }

    public static Map<String,String> parse(String raw) {
        if(raw==null || raw.isEmpty() || raw.length()>8192) throw new IllegalArgumentException("Query không hợp lệ.");
        var p=new TreeMap<String,String>();
        for(String pair:raw.split("&",-1)) {
            String[] kv=pair.split("=",2);
            if(kv.length!=2)throw new IllegalArgumentException("Tham số không hợp lệ.");
            String key=URLDecoder.decode(kv[0],StandardCharsets.UTF_8),value=URLDecoder.decode(kv[1],StandardCharsets.UTF_8);
            if(!key.matches("vnp_[A-Za-z0-9_]{1,64}") || value.length()>2048 || p.putIfAbsent(key,value)!=null || p.size()>64)
                throw new IllegalArgumentException("Tham số không hợp lệ hoặc bị lặp.");
        }
        return p;
    }

    public String signature(Map<String,String> p) {
        try {
            Mac mac=Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.secret().getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(p).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException e) {throw new IllegalStateException("Không thể ký VNPay.",e);}
    }
    public static String canonical(Map<String,String> p) {
        return new TreeMap<>(p).entrySet().stream().filter(e->!e.getValue().isEmpty())
                .map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(Collectors.joining("&"));
    }
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static String scaled(BigDecimal amount) {
        if(amount==null || amount.signum()<=0)throw new IllegalArgumentException("Số tiền phải dương.");
        String result=amount.movePointRight(2).toBigIntegerExact().toString();
        if(result.length()>12)throw new IllegalArgumentException("Số tiền vượt giới hạn VNPay.");
        return result;
    }
}
