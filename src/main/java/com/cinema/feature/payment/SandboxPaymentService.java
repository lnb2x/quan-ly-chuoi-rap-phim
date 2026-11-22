package com.cinema.feature.payment;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.shared.config.SepayConfig;
import com.cinema.shared.db.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SandboxPaymentService {

    public record Checkout(
            int bookingId,
            int paymentId,
            String transferCode,
            BigDecimal amount,
            String bankCode,
            String accountNumber,
            String accountName,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            URI qrImageUri
    ) {
        public Checkout {
            if (transferCode == null || !transferCode.matches("^SP[1-9][0-9]{0,8}P[1-9][0-9]{0,8}$")) {
                throw new IllegalArgumentException("Mã chuyển khoản SePay không hợp lệ.");
            }
        }
    }

    public record CheckoutStatus(
            int bookingId,
            String bookingStatus,
            String paymentStatus,
            String transactionRef
    ) {
    }

    public record WebhookResult(
            int httpStatus,
            boolean success,
            String code,
            String message
    ) {
    }

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int HOLD_MINUTES = 15;

    private final SandboxPaymentRepository repository;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final TransactionTemplate tx;
    private final SepayConfig config;
    private final SepayGateway gateway;
    private final SepayGateway.QrImageFetcher qrFetcher;
    private final Clock clock;

    public SandboxPaymentService(
            SandboxPaymentRepository repository,
            BookingRepository bookings,
            TransactionTemplate tx,
            SepayConfig config,
            Clock clock
    ) {
        this(repository, bookings, tx, config, clock, SepayGateway.defaultQrImageFetcher());
    }

    public SandboxPaymentService(
            SandboxPaymentRepository repository,
            BookingRepository bookings,
            TransactionTemplate tx,
            SepayConfig config,
            Clock clock,
            SepayGateway.QrImageFetcher qrFetcher
    ) {
        repository.assertTestDatabase();
        this.repository = repository;
        this.bookings = bookings;
        this.tx = tx;
        this.config = config;
        this.gateway = new SepayGateway(config);
        this.qrFetcher = qrFetcher;
        this.clock = clock;
        this.bookingService = new BookingService(bookings, tx, clock);
    }

    public Checkout create(int screeningId, Integer customerId, int employeeId, List<Integer> seats) {
        config.requireCredentials();
        int bookingId = bookingService.createSandboxBooking(screeningId, customerId, employeeId, seats);
        try {
            return tx.execute(c -> {
                var payment = repository.lock(c, bookingId, 0);
                if (payment == null) {
                    throw new IllegalStateException("Không tìm thấy giao dịch thanh toán cho đơn #" + bookingId);
                }
                String transferCode = "SP" + bookingId + "P" + payment.paymentId();
                LocalDateTime expiresAt = payment.created().plusMinutes(HOLD_MINUTES);
                URI qrUri = gateway.qrImageUri(transferCode, payment.amount());
                return new Checkout(
                        bookingId,
                        payment.paymentId(),
                        transferCode,
                        payment.amount(),
                        config.bankCode(),
                        config.accountNumber(),
                        config.accountName(),
                        payment.created(),
                        expiresAt,
                        qrUri
                );
            });
        } catch (RuntimeException ex) {
            try {
                bookingService.cancelBooking(bookingId);
            } catch (RuntimeException cancelEx) {
                ex.addSuppressed(cancelEx);
            }
            throw ex;
        }
    }

    public byte[] fetchQrImage(URI qrImageUri) throws IOException {
        return qrFetcher.fetchPng(qrImageUri);
    }

    public Optional<CheckoutStatus> statusOf(int bookingId) {
        return repository.findCheckoutStatus(bookingId);
    }

    public WebhookResult webhook(Map<String, List<String>> headers, byte[] rawBody) {
        try {
            gateway.verifyAuth(headers, rawBody, clock);
        } catch (SecurityException | IllegalStateException ex) {
            return new WebhookResult(401, false, "INVALID_SIGNATURE", "Xác thực Webhook SePay không hợp lệ.");
        }

        final SepayWebhookEvent event;
        try {
            event = SepayJsonParser.parseWebhookEvent(new String(rawBody, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return new WebhookResult(400, false, "INVALID_PAYLOAD", "Dữ liệu JSON Webhook không hợp lệ.");
        }

        if (!config.matchesReceivingAccount(event.accountNumber(), event.subAccount())) {
            return new WebhookResult(422, false, "ACCOUNT_MISMATCH", "Tài khoản nhận không khớp cấu hình SePay Sandbox.");
        }
        if (!"in".equals(event.transferType())) {
            return new WebhookResult(422, false, "IGNORED_TRANSFER_TYPE", "Chỉ xử lý biến động tiền vào (transferType=in).");
        }

        final Optional<SepayGateway.ReferenceToken> refOpt;
        try {
            refOpt = SepayGateway.extractReference(event.code(), event.content());
        } catch (IllegalStateException ex) {
            return new WebhookResult(422, false, "AMBIGUOUS_REFERENCE", "Nội dung chuyển khoản chứa nhiều mã đơn mâu thuẫn.");
        }
        if (refOpt.isEmpty()) {
            return new WebhookResult(422, false, "ORDER_NOT_FOUND", "Không tìm thấy mã đơn SePay trong nội dung chuyển khoản.");
        }

        SepayGateway.ReferenceToken ref = refOpt.get();
        return processMatchedTransaction(ref.bookingId(), ref.paymentId(), event);
    }

    private WebhookResult processMatchedTransaction(int bookingId, int paymentId, SepayWebhookEvent event) {
        String successRef = "SEPAY-SUCCESS-" + event.id();
        String lateRef = "SEPAY-LATE-" + event.id();

        return tx.execute(c -> {
            repository.acquireProviderTxLock(c, event.id());
            var p = repository.lock(c, bookingId, paymentId);
            if (p == null || p.reference() == null || !p.reference().startsWith("SEPAY-")) {
                return new WebhookResult(422, false, "ORDER_NOT_FOUND", "Đơn đặt vé SePay không tồn tại.");
            }
            if (repository.isProviderTxUsedElsewhere(c, event.id(), p.paymentId())) {
                return new WebhookResult(422, false, "CONFLICTING_TRANSACTION", "Mã giao dịch SePay đã gắn với đơn khác.");
            }
            if ("SUCCESS".equals(p.paymentStatus())) {
                return p.reference().equals(successRef)
                        ? new WebhookResult(200, true, "ALREADY_PROCESSED", "Giao dịch đã được ghi nhận trước đó.")
                        : new WebhookResult(422, false, "CONFLICTING_TRANSACTION", "Đơn đã thanh toán bằng mã giao dịch khác.");
            }
            if (p.reference().startsWith("SEPAY-LATE-")) {
                return p.reference().equals(lateRef)
                        ? new WebhookResult(200, true, "ALREADY_PROCESSED", "Giao dịch đến muộn đã được ghi dấu trước đó.")
                        : new WebhookResult(422, false, "CONFLICTING_TRANSACTION", "Đơn đã có dấu đối soát muộn khác.");
            }
            if (p.amount().compareTo(event.transferAmount()) != 0) {
                return new WebhookResult(422, false, "AMOUNT_MISMATCH", "Số tiền chuyển khoản không khớp tổng tiền vé.");
            }
            if ("CANCELLED".equals(p.bookingStatus()) || !now().isBefore(p.created().plusMinutes(HOLD_MINUTES))) {
                if ("PENDING".equals(p.bookingStatus())) {
                    bookings.cancelBookingRecords(c, bookingId);
                }
                repository.reference(c, paymentId, lateRef);
                return new WebhookResult(200, true, "LATE_PAYMENT_RECORDED",
                        "Đơn đã hết hạn hoặc đã hủy; đã ghi dấu SEPAY-LATE để đối soát thủ công.");
            }
            if (!"PENDING".equals(p.bookingStatus()) || !"PENDING".equals(p.paymentStatus())) {
                return new WebhookResult(200, true, "ALREADY_PROCESSED", "Trạng thái đơn đã kết thúc.");
            }
            bookings.transitionPendingPayment(c, bookingId, "SUCCESS");
            repository.reference(c, paymentId, successRef);
            return new WebhookResult(200, true, "CONFIRMED", "Đã xác nhận thanh toán vé thành công qua SePay.");
        });
    }

    public void expirePending() {
        for (int id : repository.expired(now().minusMinutes(HOLD_MINUTES))) {
            tx.execute(c -> {
                var p = repository.lock(c, id, 0);
                if (p != null
                        && "PENDING".equals(p.bookingStatus())
                        && "PENDING".equals(p.paymentStatus())
                        && p.reference() != null
                        && p.reference().startsWith("SEPAY-")
                        && !now().isBefore(p.created().plusMinutes(HOLD_MINUTES))) {
                    bookings.cancelBookingRecords(c, id);
                }
                return null;
            });
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
    }
}
