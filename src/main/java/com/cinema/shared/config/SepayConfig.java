package com.cinema.shared.config;

import java.util.Locale;
import java.util.Objects;

/**
 * Cấu hình SePay Sandbox (Test Mode VietQR + Webhook JSON).
 * Tách biệt hoàn toàn trên môi trường kiểm thử và không bao giờ in lộ khóa bí mật qua {@link #toString()}.
 */
public final class SepayConfig {

    public enum AuthMode {
        HMAC_SHA256,
        API_KEY;

        public static AuthMode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("Chế độ xác thực SePay không được để trống.");
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "HMAC_SHA256" -> HMAC_SHA256;
                case "API_KEY" -> API_KEY;
                default -> throw new IllegalArgumentException(
                        "Chế độ xác thực SePay không hợp lệ (chỉ hỗ trợ HMAC_SHA256 hoặc API_KEY): " + raw);
            };
        }
    }

    private static final int MIN_SECRET_LENGTH = 16;
    private static final int MAX_PORT = 65_535;

    private final String bankCode;
    private final String accountNumber;
    private final String accountName;
    private final AuthMode authMode;
    private final String webhookSecret;
    private final int port;

    public SepayConfig(
            String bankCode,
            String accountNumber,
            String accountName,
            AuthMode authMode,
            String webhookSecret,
            int port
    ) {
        Objects.requireNonNull(authMode, "authMode không được phép null.");
        if (port < 0 || port > MAX_PORT) {
            throw new IllegalArgumentException("Cổng lắng nghe SePay Sandbox không hợp lệ: " + port);
        }
        rejectControlChars(bankCode, "bankCode");
        rejectControlChars(accountNumber, "accountNumber");
        rejectControlChars(accountName, "accountName");
        rejectControlChars(webhookSecret, "webhookSecret");

        this.bankCode = bankCode == null ? "" : bankCode.trim();
        this.accountNumber = accountNumber == null ? "" : accountNumber.trim();
        this.accountName = (accountName == null || accountName.isBlank()) ? "CINEVIET SANDBOX" : accountName.trim();
        this.authMode = authMode;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
        this.port = port;
    }

    public static SepayConfig load() {
        var props = AppConfig.loadLocalPropertiesFile();
        String bank = resolve("SEPAY_BANK_CODE", props.getProperty("sepay.bank-code"), "");
        String acc = resolve("SEPAY_ACCOUNT_NUMBER", props.getProperty("sepay.account-number"), "");
        String name = resolve("SEPAY_ACCOUNT_NAME", props.getProperty("sepay.account-name"), "CINEVIET SANDBOX");
        String modeRaw = resolve("SEPAY_AUTH_MODE", props.getProperty("sepay.auth-mode"), "HMAC_SHA256");
        String secret = resolve("SEPAY_WEBHOOK_SECRET", props.getProperty("sepay.webhook-secret"), "");
        String portRaw = resolve("SEPAY_PORT", props.getProperty("sepay.port"), "8889");
        return new SepayConfig(bank, acc, name, AuthMode.parse(modeRaw), secret, Integer.parseInt(portRaw.trim()));
    }

    public void requireCredentials() {
        if (!bankCode.matches("[A-Za-z0-9]{2,20}")
                || !accountNumber.matches("[A-Za-z0-9]{4,34}")
                || webhookSecret.isBlank()
                || webhookSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Chưa cấu hình tài khoản nhận hoặc khóa xác thực SePay Sandbox trong config/application.properties. Không tạo đơn.");
        }
    }

    public boolean matchesReceivingAccount(String incomingAccount, String incomingSubAccount) {
        if (accountNumber.isEmpty()) {
            return false;
        }
        return accountNumber.equals(incomingAccount) || accountNumber.equals(incomingSubAccount);
    }

    public String bankCode() {
        return bankCode;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public String accountName() {
        return accountName;
    }

    public AuthMode authMode() {
        return authMode;
    }

    public String webhookSecret() {
        return webhookSecret;
    }

    public int port() {
        return port;
    }

    @Override
    public String toString() {
        String maskedAcc = accountNumber.length() <= 4
                ? "***"
                : "***" + accountNumber.substring(accountNumber.length() - 4);
        return "SepayConfig[bankCode=" + bankCode
                + ", account=" + maskedAcc
                + ", authMode=" + authMode
                + ", port=" + port
                + ", secret=***]";
    }

    private static String resolve(String envKey, String propVal, String fallback) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        if (propVal != null && !propVal.isBlank()) {
            return propVal;
        }
        return fallback;
    }

    private static void rejectControlChars(String value, String fieldName) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0') {
                throw new IllegalArgumentException("Trường cấu hình " + fieldName + " chứa ký tự điều khiển không hợp lệ.");
            }
        }
    }
}
