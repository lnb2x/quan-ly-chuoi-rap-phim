package com.cinema.unit;

import com.cinema.domain.Movie;
import com.cinema.domain.ScreeningRoom;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị (Unit Tests) cho các quy tắc kiểm tra hợp lệ dữ liệu đầu vào
 * của Quản lý Suất chiếu và Đặt vé (R5).
 */
class ScreeningValidationTest {

    private ScreeningService screeningService;
    private BookingService bookingService;
    private Movie sampleMovie;
    private ScreeningRoom activeRoom;

    @BeforeEach
    void setUp() {
        AppConfig dummyConfig = AppConfig.loadTest();
        ConnectionProvider cp = new ConnectionProvider(dummyConfig);
        TransactionTemplate tx = new TransactionTemplate(cp);
        screeningService = new ScreeningService(new ScreeningRepository(cp), tx);
        bookingService = new BookingService(new BookingRepository(cp), tx);

        sampleMovie = new Movie(1, "MV-001", "Đào, Phở và Piano", 105,
                LocalDate.of(2026, 9, 1), "T13", "Việt Nam", "Mô tả");
        activeRoom = new ScreeningRoom(1, 1, "CineViet Vincom Bà Triệu", "Phòng 01", "2D", true);
    }

    @Test
    @DisplayName("Suất chiếu hợp lệ (đủ thời lượng phim, giá > 0, phòng đang hoạt động) phải vượt qua kiểm tra")
    void validScreeningInputShouldPass() {
        ValidationResult result = screeningService.validateScreeningInput(
                sampleMovie,
                activeRoom,
                LocalDateTime.of(2026, 11, 12, 9, 0),
                LocalDateTime.of(2026, 11, 12, 11, 5),
                new BigDecimal("95000"),
                "OPEN"
        );
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Từ chối suất chiếu có thời gian kết thúc nhỏ hơn hoặc bằng thời gian bắt đầu [BR-05]")
    void endTimeBeforeStartTimeShouldFail() {
        ValidationResult result = screeningService.validateScreeningInput(
                sampleMovie,
                activeRoom,
                LocalDateTime.of(2026, 11, 12, 14, 0),
                LocalDateTime.of(2026, 11, 12, 13, 30),
                new BigDecimal("95000"),
                "OPEN"
        );
        assertFalse(result.isValid());
        assertTrue(result.summaryMessage().contains("BR-05"));
    }

    @Test
    @DisplayName("Từ chối suất chiếu có khoảng thời gian nhỏ hơn thời lượng phim [BR-05]")
    void durationShorterThanMovieShouldFail() {
        ValidationResult result = screeningService.validateScreeningInput(
                sampleMovie,
                activeRoom,
                LocalDateTime.of(2026, 11, 12, 9, 0),
                LocalDateTime.of(2026, 11, 12, 10, 0), // 60 phút < 105 phút
                new BigDecimal("95000"),
                "OPEN"
        );
        assertFalse(result.isValid());
        assertTrue(result.summaryMessage().contains("105 phút"));
    }

    @Test
    @DisplayName("Từ chối suất chiếu có giá vé cơ sở <= 0 [BR-04]")
    void nonPositiveBasePriceShouldFail() {
        ValidationResult result = screeningService.validateScreeningInput(
                sampleMovie,
                activeRoom,
                LocalDateTime.of(2026, 11, 12, 9, 0),
                LocalDateTime.of(2026, 11, 12, 11, 10),
                BigDecimal.ZERO,
                "OPEN"
        );
        assertFalse(result.isValid());
        assertTrue(result.summaryMessage().contains("BR-04"));
    }

    @Test
    @DisplayName("Từ chối đơn đặt vé không chọn ghế hoặc có ghế trùng lặp trong cùng yêu cầu [BR-08, BR-10]")
    void bookingValidationShouldRejectEmptyOrDuplicateSeats() {
        ValidationResult emptySeats = bookingService.validateBookingRequest(
                1, 3, List.of(), "CASH", "SUCCESS");
        assertFalse(emptySeats.isValid());
        assertTrue(emptySeats.summaryMessage().contains("BR-08"));

        ValidationResult duplicateSeats = bookingService.validateBookingRequest(
                1, 3, List.of(10, 10), "CASH", "SUCCESS");
        assertFalse(duplicateSeats.isValid());
        assertTrue(duplicateSeats.summaryMessage().contains("BR-10"));
    }
}
