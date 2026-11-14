package com.cinema.shared.config;

import java.net.URI;

/** Sandbox only; deliberately has no production gateway URL or secret-bearing toString. */
public final class VnpayConfig {
    private final String merchant, secret;
    private final URI returnUrl;
    private final int port;

    public VnpayConfig(String merchant, String secret, URI returnUrl, int port) {
        if (returnUrl == null || !("http".equals(returnUrl.getScheme()) || "https".equals(returnUrl.getScheme()))
                || returnUrl.getHost() == null || returnUrl.getRawQuery() != null || returnUrl.getFragment() != null
                || returnUrl.getUserInfo() != null || port < 0 || port > 65535) {
            throw new IllegalArgumentException("Cấu hình URL/cổng VNPay Sandbox không hợp lệ.");
        }
        this.merchant=merchant; this.secret=secret; this.returnUrl=returnUrl; this.port=port;
    }

    public static VnpayConfig load() {
        var p=AppConfig.loadLocalPropertiesFile();
        return new VnpayConfig(p.getProperty("vnpay.tmn-code",""),p.getProperty("vnpay.hash-secret",""),
                URI.create(p.getProperty("vnpay.return-url","http://127.0.0.1:8889/sandbox/return")),
                Integer.parseInt(p.getProperty("vnpay.port","8889")));
    }

    public void requireCredentials() {
        if (merchant==null || !merchant.matches("[A-Za-z0-9]{8}") || secret==null || secret.length()<16) {
            throw new IllegalStateException("Chưa cấu hình TmnCode/HashSecret VNPay Sandbox trong config/application.properties. Không tạo đơn.");
        }
    }
    public String merchant() {return merchant;}
    public String secret() {return secret;}
    public URI returnUrl() {return returnUrl;}
    public int port() {return port;}
}
