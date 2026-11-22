package com.cinema.feature.payment;

import java.math.BigDecimal;

/**
 * Bản ghi bất biến biểu diễn dữ liệu sự kiện Webhook JSON từ SePay.
 */
public record SepayWebhookEvent(
        long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String subAccount,
        String code,
        String content,
        String transferType,
        BigDecimal transferAmount,
        BigDecimal accumulated,
        String referenceCode,
        String description
) {
}
