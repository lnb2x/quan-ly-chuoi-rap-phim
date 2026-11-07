package com.cinema.integration;

import com.cinema.domain.ScreeningDetailView;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.search.ScreeningSearchRepository;
import com.cinema.feature.search.ScreeningSearchService;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Tích hợp Tra cứu Suất chiếu (Screen 3 - R6).
 */
class ScreeningSearchIT {

    private static ScreeningSearchService searchService;

    @BeforeAll
    static void init() {
        ConnectionProvider cp = new ConnectionProvider(AppConfig.loadTest());
        searchService = new ScreeningSearchService(
                new ScreeningSearchRepository(cp),
                new ScreeningRepository(cp)
        );
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
}
