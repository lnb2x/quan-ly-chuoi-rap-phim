package com.cinema.integration;

import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Đồng thời Lập lịch Suất chiếu (Concurrent Overlapping-Screening Verification).
 * Chứng minh rằng khi nhiều luồng đồng thời cùng tạo các suất chiếu chồng lấn thời gian
 * trong cùng một phòng chiếu, cơ chế khóa dòng phòng chiếu (SELECT ... FOR UPDATE) kết hợp
 * Trigger CSDL đảm bảo chỉ duy nhất 01 giao dịch thành công và tất cả giao dịch còn lại bị từ chối.
 */
class ScreeningConcurrencyIT {

    private static ConnectionProvider connectionProvider;
    private static ScreeningService screeningService;

    @BeforeAll
    static void init() {
        connectionProvider = TestDatabaseGuard.verifyAndCreateTestProvider();
        TransactionTemplate tx = new TransactionTemplate(connectionProvider);
        screeningService = new ScreeningService(new ScreeningRepository(connectionProvider), tx);
    }

    @Test
    @DisplayName("12 luồng đồng thời tạo suất chiếu chồng lấn trong cùng một phòng: Đúng 1 luồng thành công, 11 luồng bị từ chối [BR-06]")
    void concurrentOverlappingScreeningsMustAllowExactlyOneWinner() throws Exception {
        int roomId = 2;
        int movieId = 1; // 105 phút
        LocalDateTime baseStart = LocalDateTime.of(2026, 12, 20, 14, 0);
        LocalDateTime baseEnd = LocalDateTime.of(2026, 12, 20, 16, 15);

        // Dọn sạch dữ liệu ngày test 2026-12-20 trước khi chạy
        cleanUpTestDate(roomId, "2026-12-20");

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startSignal = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Integer> createdIds = new ArrayList<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int shiftMinutes = (i % 4) * 10; // Dịch 0, 10, 20, 30 phút nhưng vẫn giao nhau trong khoảng 14:00 - 16:15
            tasks.add(() -> {
                ready.countDown();
                startSignal.await();
                try {
                    int id = screeningService.createScreening(
                            movieId,
                            roomId,
                            baseStart.plusMinutes(shiftMinutes),
                            baseEnd.plusMinutes(shiftMinutes),
                            new BigDecimal("110000.00")
                    );
                    synchronized (createdIds) {
                        createdIds.add(id);
                    }
                    successCount.incrementAndGet();
                } catch (BusinessRuleException expected) {
                    rejectedCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.await();
        startSignal.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        try {
            assertEquals(1, successCount.get(), "Chỉ duy nhất 1 luồng được phép tạo suất chiếu thành công");
            assertEquals(threads - 1, rejectedCount.get(), "Tất cả 11 luồng chồng lấn còn lại phải bị từ chối");
        } finally {
            cleanUpTestDate(roomId, "2026-12-20");
        }
    }

    @Test
    @DisplayName("8 luồng đồng thời UPDATE dời các suất chiếu vào cùng khung giờ chồng lấn: Đúng 1 luồng thành công, 7 luồng bị từ chối [BR-06] và chặn đổi phòng khi có vé [BR-09]")
    void concurrentUpdatingScreeningsIntoOverlappingSlotMustAllowExactlyOneWinner() throws Exception {
        int roomId = 3;
        int movieId = 1; // 105 phút
        cleanUpTestDate(roomId, "2026-12-21");
        cleanUpTestDate(roomId, "2026-12-22");

        List<Integer> screeningIds = new ArrayList<>();
        try {
            // Khởi tạo 8 suất chiếu tách biệt hoàn toàn vào ngày 2026-12-21 và 2026-12-22
            for (int i = 0; i < 4; i++) {
                LocalDateTime st = LocalDateTime.of(2026, 12, 21, 8 + (i * 3), 0);
                screeningIds.add(screeningService.createScreening(
                        movieId, roomId, st, st.plusMinutes(125), new BigDecimal("100000.00")));
            }
            for (int i = 0; i < 4; i++) {
                LocalDateTime st = LocalDateTime.of(2026, 12, 22, 8 + (i * 3), 0);
                screeningIds.add(screeningService.createScreening(
                        movieId, roomId, st, st.plusMinutes(125), new BigDecimal("100000.00")));
            }

            // Đồng thời cả 8 luồng cùng gọi updateScreening dời suất chiếu của mình về khung 20:30 - 22:45 ngày 2026-12-21
            int threads = screeningIds.size();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch startSignal = new CountDownLatch(1);

            AtomicInteger updateSuccess = new AtomicInteger(0);
            AtomicInteger updateRejected = new AtomicInteger(0);

            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int scrId = screeningIds.get(i);
                final int shift = (i % 3) * 10;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    startSignal.await();
                    try {
                        screeningService.updateScreening(
                                scrId,
                                movieId,
                                roomId,
                                LocalDateTime.of(2026, 12, 21, 20, 30).plusMinutes(shift),
                                LocalDateTime.of(2026, 12, 21, 22, 45).plusMinutes(shift),
                                new BigDecimal("105000.00"),
                                "OPEN"
                        );
                        updateSuccess.incrementAndGet();
                    } catch (BusinessRuleException expected) {
                        updateRejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            ready.await();
            startSignal.countDown();
            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();

            assertEquals(1, updateSuccess.get(), "Chỉ đúng 1 giao dịch UPDATE được phép dời vào khung giờ đích");
            assertEquals(threads - 1, updateRejected.get(), "7 giao dịch UPDATE chồng lấn còn lại phải bị từ chối [BR-06]");

            // Kiểm chứng tầng Service cũng từ chối đổi phòng của suất chiếu đã phát sinh vé (#1)
            BusinessRuleException roomChangeEx = assertThrows(BusinessRuleException.class, () ->
                    screeningService.updateScreening(
                            1,
                            1,
                            2,
                            LocalDateTime.of(2026, 12, 22, 21, 0),
                            LocalDateTime.of(2026, 12, 22, 23, 15),
                            new BigDecimal("95000.00"),
                            "OPEN"
                    ));
            assertTrue(roomChangeEx.getMessage().contains("BR-09"));
        } finally {
            cleanUpTestDate(roomId, "2026-12-21");
            cleanUpTestDate(roomId, "2026-12-22");
        }
    }

    private void cleanUpTestDate(int roomId, String dateIso) throws Exception {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM screening WHERE room_id = ? AND DATE(start_time) = ?")) {
            ps.setInt(1, roomId);
            ps.setString(2, dateIso);
            ps.executeUpdate();
        }
    }
}
