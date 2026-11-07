package com.cinema.integration;

import com.cinema.domain.ScreeningDetailView;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.feature.search.ScreeningSearchRepository;
import com.cinema.feature.search.ScreeningSearchService;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Tích hợp Tra cứu Suất chiếu & Hiển thị Danh sách Suất mở bán (Screen 2 & Screen 3).
 */
class ScreeningSearchIT {

    private static ConnectionProvider cp;
    private static ScreeningSearchService searchService;
    private static ScreeningService screeningService;
    private static BookingService bookingService;

    @BeforeAll
    static void init() {
        cp = TestDatabaseGuard.verifyAndCreateTestProvider();
        TransactionTemplate tx = new TransactionTemplate(cp);
        ScreeningRepository screeningRepository = new ScreeningRepository(cp);
        searchService = new ScreeningSearchService(
                new ScreeningSearchRepository(cp),
                screeningRepository
        );
        screeningService = new ScreeningService(screeningRepository, tx);
        bookingService = new BookingService(new BookingRepository(cp), tx);
    }

    @Test
    @DisplayName("Tra cứu suất chiếu theo ngày, chi nhánh và khung giờ phải trả về số ghế trống chính xác (totalSeats - soldSeats)")
    void searchScreeningsShouldReturnAccurateSeatAvailability() {
        List<ScreeningDetailView> list = searchService.search(
                "",
                null,
                1, // Chi nhánh Hà Nội
                LocalDate.of(2026, 11, 4),
                LocalTime.of(8, 0),
                LocalTime.of(23, 0),
                true
        );
        assertFalse(list.isEmpty(), "Phải tìm thấy các suất chiếu tại chi nhánh Hà Nội ngày 04/11/2026");
        for (ScreeningDetailView s : list) {
            assertEquals(1, s.branchId());
            assertEquals(LocalDate.of(2026, 11, 4), s.startTime().toLocalDate());
            assertEquals("OPEN", s.status());
            assertEquals(80, s.totalSeats());
            assertEquals(s.totalSeats() - s.soldSeats(), s.availableSeats());
            assertTrue(s.availableSeats() > 0);
        }
    }

    @Test
    @DisplayName("Danh sách suất chiếu mở bán trên Màn hình Đặt vé phải trả về đầy đủ toàn bộ suất OPEN (> 120 suất) và thấy ngay suất mới tạo ở cuối lịch")
    void openScreeningsDropdownShouldIncludeNewlyCreatedFutureScreeningBeyond120Rows() throws Exception {
        int createdScreeningId = 0;
        try {
            createdScreeningId = screeningService.createScreening(
                    1,
                    1,
                    LocalDateTime.of(2026, 12, 28, 19, 0),
                    LocalDateTime.of(2026, 12, 28, 21, 15),
                    new BigDecimal("110000")
            );
            int expectedOpenCount;
            try (Connection conn = cp.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM screening WHERE status = 'OPEN'");
                 var rs = ps.executeQuery()) {
                rs.next();
                expectedOpenCount = rs.getInt(1);
            }
            final int expectedId = createdScreeningId;
            List<ScreeningDetailView> openScreenings = bookingService.listOpenScreenings(null, null);
            assertTrue(expectedOpenCount > 120, "Tập dữ liệu kiểm thử phải có hơn 120 suất OPEN");
            assertEquals(expectedOpenCount, openScreenings.size(),
                    "Danh sách suất chiếu mở bán phải khớp 100% tổng số suất OPEN trong MySQL (không bị cắt cụt ở 120 dòng)");
            assertTrue(openScreenings.stream().anyMatch(s -> s.screeningId() == expectedId),
                    "Suất chiếu mới tạo #" + expectedId + " phải xuất hiện ngay trong danh sách chọn của Màn hình Đặt vé");
        } finally {
            if (createdScreeningId > 0) {
                try (Connection conn = cp.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM screening WHERE screening_id = ?")) {
                    ps.setInt(1, createdScreeningId);
                    ps.executeUpdate();
                }
            }
        }
    }
}
