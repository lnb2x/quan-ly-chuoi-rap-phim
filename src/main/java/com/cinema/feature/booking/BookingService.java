package com.cinema.feature.booking;

import com.cinema.domain.BookingSummaryView;
import com.cinema.domain.Customer;
import com.cinema.domain.Employee;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.SeatAvailabilityView;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.shared.validation.ValidationResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tầng dịch vụ nghiệp vụ cho Đặt vé & Chọn ghế (Screen 2 - R5).
 * Đảm bảo giao dịch nguyên tử (Atomic Transaction), chống bán trùng ghế ngay cả
 * dưới nhiều luồng đồng thời, bảo toàn giá vé lịch sử và hỗ trợ hủy vé / bán lại ghế.
 */
public final class BookingService {

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger(100);

    private final BookingRepository repository;
    private final TransactionTemplate transactionTemplate;

    public BookingService(BookingRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ScreeningDetailView> listOpenScreenings(Integer branchId, Integer movieId) {
        return repository.findOpenScreenings(branchId, movieId);
    }

    public List<Customer> listCustomers() {
        return repository.findAllCustomers();
    }

    public List<Employee> listEmployees() {
        return repository.findAllEmployees();
    }

    public List<SeatAvailabilityView> getSeatMap(int screeningId) {
        return repository.findSeatsForScreening(screeningId);
    }

    public List<BookingSummaryView> listRecentBookings(Integer screeningId, int limit) {
        return repository.findRecentBookings(screeningId, limit);
    }

    public ValidationResult validateBookingRequest(
            Integer screeningId,
            Integer employeeId,
            List<Integer> seatIds,
            String paymentMethod,
            String paymentStatus
    ) {
        ValidationResult vr = new ValidationResult();
        if (screeningId == null || screeningId <= 0) {
            vr.addError("Vui lòng chọn suất chiếu hợp lệ.");
        }
        if (employeeId == null || employeeId <= 0) {
            vr.addError("Vui lòng chọn nhân viên thu ngân thực hiện giao dịch.");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            vr.addError("Vui lòng chọn ít nhất 01 ghế trên sơ đồ phòng chiếu [BR-08].");
        } else {
            Set<Integer> distinct = new LinkedHashSet<>(seatIds);
            if (distinct.size() != seatIds.size()) {
                vr.addError("Danh sách ghế đặt trong cùng một đơn không được chứa ghế trùng lặp [BR-10].");
            }
        }
        if (paymentMethod == null || !List.of("CASH", "CARD", "TRANSFER", "EWALLET").contains(paymentMethod)) {
            vr.addError("Phương thức thanh toán không hợp lệ.");
        }
        if (paymentStatus == null || !List.of("SUCCESS", "PENDING").contains(paymentStatus)) {
            vr.addError("Trạng thái thanh toán khi tạo đơn chỉ nhận SUCCESS hoặc PENDING.");
        }
        return vr;
    }

    /**
     * Thực hiện đặt vé nguyên tử (Atomic Booking Transaction):
     * 1. Khóa dòng suất chiếu (SELECT ... FOR UPDATE) để tuần tự hóa các giao dịch đồng thời.
     * 2. Kiểm tra từng ghế thuộc đúng phòng chiếu của suất chiếu (BR-09) và chưa có vé VALID (BR-10).
     * 3. Tính đơn giá lịch sử unit_price = base_price + surcharge (BR-12).
     * 4. Tạo booking, các dòng ticket và dòng payment trong cùng một giao dịch.
     */
    public int createBooking(
            int screeningId,
            Integer customerId,
            int employeeId,
            List<Integer> seatIds,
            String paymentMethod,
            String paymentStatus
    ) {
        validateBookingRequest(screeningId, employeeId, seatIds, paymentMethod, paymentStatus).throwIfInvalid();

        return transactionTemplate.execute(conn -> {
            BookingRepository.LockedScreeningInfo scr = repository.lockScreeningForUpdate(conn, screeningId);
            if (!"OPEN".equals(scr.status())) {
                throw new BusinessRuleException(
                        "Lỗi nghiệp vụ [BR-08]: Suất chiếu #" + screeningId + " đã bị hủy hoặc ngừng nhận đặt vé.");
            }

            BigDecimal totalAmount = BigDecimal.ZERO;
            List<BookingRepository.SeatPriceInfo> verifiedSeats = new java.util.ArrayList<>();

            for (int seatId : seatIds) {
                BookingRepository.SeatPriceInfo seatInfo = repository.inspectSeatForBooking(conn, screeningId, seatId);
                if (seatInfo.roomId() != scr.roomId()) {
                    throw new BusinessRuleException(String.format(
                            "Lỗi nghiệp vụ [BR-09]: Ghế %s (#%d) không thuộc phòng chiếu của suất chiếu #%d.",
                            seatInfo.seatLabel(), seatId, screeningId));
                }
                if (seatInfo.alreadyOccupied()) {
                    throw new BusinessRuleException(String.format(
                            "Lỗi nghiệp vụ [BR-10]: Ghế %s đã được bán cho khách hàng khác trong suất chiếu #%d.",
                            seatInfo.seatLabel(), screeningId));
                }
                verifiedSeats.add(seatInfo);
                totalAmount = totalAmount.add(scr.basePrice().add(seatInfo.surcharge()));
            }

            LocalDateTime now = LocalDateTime.now();
            String bookingCode = "BK-" + now.format(CODE_FMT) + "-" + SEQ.incrementAndGet();
            String bookingStatus = "SUCCESS".equals(paymentStatus) ? "CONFIRMED" : "PENDING";

            int bookingId = repository.insertBooking(
                    conn, bookingCode, screeningId, customerId, employeeId, now, bookingStatus);

            for (BookingRepository.SeatPriceInfo seatInfo : verifiedSeats) {
                BigDecimal unitPrice = scr.basePrice().add(seatInfo.surcharge());
                repository.insertTicket(conn, bookingId, screeningId, seatInfo.seatId(), unitPrice, "VALID");
            }

            String txnRef = "TXN-" + paymentMethod + "-" + bookingId;
            LocalDateTime paidAt = "SUCCESS".equals(paymentStatus) ? now : null;
            repository.insertPayment(conn, bookingId, totalAmount, paymentMethod, paymentStatus, paidAt, txnRef);

            return bookingId;
        });
    }

    /**
     * Hủy đơn đặt vé: cập nhật trạng thái đơn sang CANCELLED, hủy toàn bộ vé (giải phóng ghế để bán lại)
     * và chuyển thanh toán sang REFUNDED (nếu đã SUCCESS) hoặc FAILED (nếu đang PENDING).
     */
    public void cancelBooking(int bookingId) {
        transactionTemplate.execute(conn -> {
            repository.cancelBookingRecords(conn, bookingId);
            return null;
        });
    }

    /**
     * Xác nhận thanh toán thành công hoặc thất bại cho đơn đang chờ (PENDING).
     */
    public void transitionPendingPayment(int bookingId, String targetPaymentStatus) {
        transactionTemplate.execute(conn -> {
            repository.transitionPendingPayment(conn, bookingId, targetPaymentStatus);
            return null;
        });
    }
}
