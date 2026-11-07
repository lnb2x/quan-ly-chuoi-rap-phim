package com.cinema.gui;

import com.cinema.app.AppShell;
import com.cinema.domain.BookingSummaryView;
import com.cinema.domain.RevenueReportData;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.ui.Theme;
import com.cinema.support.TestDatabaseGuard;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Kiểm chứng Bảo toàn Dữ liệu qua Đóng & Mở lại Ứng dụng Bình thường (Layer E — Section 4.E).
 * <p>
 * Hỗ trợ chạy qua 2 tiến trình JVM tách biệt hoàn toàn ({@code --session1 <stateFile>} rồi {@code --session2 <stateFile>})
 * hoặc chạy nối tiếp trong một lần gọi không tham số trên {@code cinema_chain_test} (tuyệt đối không chạm {@code cinema_chain}):
 * <ol>
 *   <li>Phiên 1 (JVM #1): Mở {@link AppShell}, thao tác qua GUI tạo mới 1 Suất chiếu (Màn hình 1),
 *       chọn đúng suất chiếu đó trên Màn hình 2 và đặt 2 ghế + thanh toán SUCCESS.</li>
 *   <li>Ghi nhận đầy đủ: {@code screening_id}, {@code booking_id}, {@code ticket_id}, {@code payment_id},
 *       đơn giá và Tổng số trên Báo cáo Doanh thu (Màn hình 4) & Báo cáo Bán vé (Màn hình 5).</li>
 *   <li>Đóng hoàn toàn cửa sổ và kết thúc tiến trình Phiên 1.</li>
 *   <li>Phiên 2 (JVM #2 mới hoàn toàn): Mở lại {@link AppShell} theo đúng luồng khởi động bình thường
 *       (không chạy bất kỳ lệnh reset/DROP/TRUNCATE/DELETE nào).</li>
 *   <li>Đối chiếu toàn bộ dữ liệu hiển thị trên GUI và Tổng báo cáo Trước khi đóng vs. Sau khi mở lại khớp 100%.</li>
 * </ol>
 */
public final class AppPersistenceVerificationRunner {

    private static final String TEST_SCREENING_DATE = "2026-12-27";

    private AppPersistenceVerificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig testConfig = AppConfig.loadTest();
        TestDatabaseGuard.assertSafeTestConfig(testConfig);
        ConnectionProvider cp = new ConnectionProvider(testConfig);
        TestDatabaseGuard.verifyLiveTestConnection(cp);

        Theme.setupLookAndFeel();

        String mode = args.length >= 1 ? args[0].trim() : "--both";
        Path stateFile = args.length >= 2
                ? Path.of(args[1])
                : Path.of("target", "persistence-session-state.properties");

        try {
            if ("--session1".equals(mode)) {
                cleanUpPersistenceTestDate(cp);
                runSession1AndSaveState(testConfig, cp, stateFile);
            } else if ("--session2".equals(mode)) {
                try {
                    runSession2AndVerifyState(testConfig, cp, stateFile);
                } finally {
                    cleanUpPersistenceTestDate(cp);
                    Files.deleteIfExists(stateFile);
                }
            } else {
                cleanUpPersistenceTestDate(cp);
                try {
                    runSession1AndSaveState(testConfig, cp, stateFile);
                    runSession2AndVerifyState(testConfig, cp, stateFile);
                } finally {
                    cleanUpPersistenceTestDate(cp);
                    Files.deleteIfExists(stateFile);
                }
            }
        } finally {
            disposeAllWindowsSafely();
        }
    }

    private static void runSession1AndSaveState(
            AppConfig testConfig,
            ConnectionProvider cp,
            Path stateFile
    ) throws Exception {
        String todayIso = LocalDate.now().toString();
        final int[] createdScreeningId = new int[1];
        final int[] createdBookingId = new int[1];
        final String[] createdBookingCode = new String[1];
        List<String> recordedTicketsBefore = new ArrayList<>();
        final int[] createdPaymentId = new int[1];
        final BigDecimal[] recordedPaymentAmount = new BigDecimal[1];
        final BigDecimal[] revReportTotalBefore = new BigDecimal[1];
        final int[] revReportBookingsBefore = new int[1];
        final BigDecimal[] salesReportTotalBefore = new BigDecimal[1];
        final int[] salesReportTicketsBefore = new int[1];

        System.out.println("=== [PERSISTENCE-TEST] SESSION 1: OPEN APPLICATION & CREATE DATA VIA SWING GUI ===");
        final AppShell[] s1Holder = new AppShell[1];
        SwingUtilities.invokeAndWait(() -> {
            AppShell s1 = new AppShell(testConfig);
            s1.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            s1.setSize(1420, 880);
            s1.setVisible(true);
            s1Holder[0] = s1;
        });
        AppShell shell1 = s1Holder[0];
        try {
            Thread.sleep(300);

            // 1. Tạo Suất chiếu qua Màn hình 1
            SwingUtilities.invokeAndWait(() -> {
                shell1.showScreen(AppShell.TAB_SCREENING);
                shell1.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, TEST_SCREENING_DATE, "15:00", "17:15", "105000");
                shell1.getScreeningPanel().handleCreateScreening();
                shell1.getScreeningPanel().filterByDateForVerification(TEST_SCREENING_DATE);
                List<ScreeningDetailView> rows = shell1.getScreeningPanel().getCurrentRows();
                if (rows.isEmpty()) {
                    throw new IllegalStateException("Phiên 1: Không tìm thấy suất chiếu vừa tạo ngày " + TEST_SCREENING_DATE);
                }
                createdScreeningId[0] = rows.getFirst().screeningId();
            });

            // 2. Chọn đúng suất chiếu vừa tạo trên Màn hình 2 và đặt 2 ghế + thanh toán SUCCESS
            SwingUtilities.invokeAndWait(() -> {
                shell1.showScreen(AppShell.TAB_BOOKING);
                if (!shell1.getBookingPanel().selectScreeningById(createdScreeningId[0])) {
                    throw new IllegalStateException("Phiên 1: Không chọn được suất chiếu #" + createdScreeningId[0] + " trên Màn hình 2");
                }
                shell1.getBookingPanel().selectFirstAvailableSeatsForDemo(2);
                shell1.getBookingPanel().handleCreateBooking();
                shell1.getBookingPanel().selectScreeningById(createdScreeningId[0]);
                List<BookingSummaryView> bks = shell1.getBookingPanel().getRecentBookings();
                if (bks.isEmpty()) {
                    throw new IllegalStateException("Phiên 1: Không tìm thấy đơn đặt vé vừa tạo trên Màn hình 2");
                }
                createdBookingId[0] = bks.getFirst().bookingId();
                createdBookingCode[0] = bks.getFirst().bookingCode();
            });

            // Đọc chi tiết ID của ticket và payment để ghi biên bản đối chiếu
            try (Connection conn = cp.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ticket_id, seat_id, unit_price, ticket_status FROM ticket WHERE booking_id = ? ORDER BY ticket_id ASC")) {
                    ps.setInt(1, createdBookingId[0]);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            recordedTicketsBefore.add(String.format("ticket_id=%d(seat=%d,price=%s,status=%s)",
                                    rs.getInt("ticket_id"),
                                    rs.getInt("seat_id"),
                                    rs.getBigDecimal("unit_price").toPlainString(),
                                    rs.getString("ticket_status")));
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT payment_id, amount, payment_method, payment_status FROM payment WHERE booking_id = ?")) {
                    ps.setInt(1, createdBookingId[0]);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Phiên 1: Không tìm thấy payment cho booking #" + createdBookingId[0]);
                        }
                        createdPaymentId[0] = rs.getInt("payment_id");
                        recordedPaymentAmount[0] = rs.getBigDecimal("amount");
                    }
                }
            }

            // 3. Ghi nhận tổng Báo cáo Doanh thu & Báo cáo Bán vé trên Màn hình 4 và Màn hình 5 trước khi đóng App
            SwingUtilities.invokeAndWait(() -> {
                shell1.showScreen(AppShell.TAB_REVENUE_REPORT);
                shell1.getRevenueReportPanel().setDateRangeForVerification(todayIso, todayIso);
                shell1.getRevenueReportPanel().refreshPreview();
                RevenueReportData revBefore = shell1.getRevenueReportPanel().getCurrentReportData();
                revReportTotalBefore[0] = revBefore.totalPaidRevenue();
                revReportBookingsBefore[0] = revBefore.totalPaidBookings();

                shell1.showScreen(AppShell.TAB_SALES_REPORT);
                shell1.getSalesReportPanel().setDateRangeForVerification(TEST_SCREENING_DATE, TEST_SCREENING_DATE);
                shell1.getSalesReportPanel().refreshPreview();
                TicketSalesReportData salesBefore = shell1.getSalesReportPanel().getCurrentReportData();
                salesReportTotalBefore[0] = salesBefore.totalSalesRevenue();
                salesReportTicketsBefore[0] = salesBefore.totalTicketsSold();
            });

            Properties props = new Properties();
            props.setProperty("todayIso", todayIso);
            props.setProperty("screeningId", Integer.toString(createdScreeningId[0]));
            props.setProperty("bookingId", Integer.toString(createdBookingId[0]));
            props.setProperty("bookingCode", createdBookingCode[0]);
            props.setProperty("tickets", String.join(";", recordedTicketsBefore));
            props.setProperty("paymentId", Integer.toString(createdPaymentId[0]));
            props.setProperty("paymentAmount", recordedPaymentAmount[0].toPlainString());
            props.setProperty("revReportTotal", revReportTotalBefore[0].toPlainString());
            props.setProperty("revReportBookings", Integer.toString(revReportBookingsBefore[0]));
            props.setProperty("salesReportTotal", salesReportTotalBefore[0].toPlainString());
            props.setProperty("salesReportTickets", Integer.toString(salesReportTicketsBefore[0]));

            if (stateFile.getParent() != null) {
                Files.createDirectories(stateFile.getParent());
            }
            try (OutputStream out = Files.newOutputStream(stateFile)) {
                props.store(out, "Before-Close GUI Persistence Snapshot");
            }

            System.out.printf("[BEFORE-CLOSE] screening_id=%d | booking_id=%d (%s) | tickets=%s | payment_id=%d (amount=%s)%n",
                    createdScreeningId[0], createdBookingId[0], createdBookingCode[0],
                    recordedTicketsBefore, createdPaymentId[0], recordedPaymentAmount[0].toPlainString());
            System.out.printf("[BEFORE-CLOSE-REPORTS] RevenueReport(%s)=%s (%d bookings) | SalesReport(%s)=%s (%d tickets)%n",
                    todayIso, revReportTotalBefore[0].toPlainString(), revReportBookingsBefore[0],
                    TEST_SCREENING_DATE, salesReportTotalBefore[0].toPlainString(), salesReportTicketsBefore[0]);
        } finally {
            SwingUtilities.invokeAndWait(shell1::dispose);
            Thread.sleep(200);
            System.out.println("=== [PERSISTENCE-TEST] SESSION 1 CLOSED COMPLETELY ===");
        }
    }

    private static void runSession2AndVerifyState(
            AppConfig testConfig,
            ConnectionProvider cp,
            Path stateFile
    ) throws Exception {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        }
        String todayIso = props.getProperty("todayIso");
        int expectedScreeningId = Integer.parseInt(props.getProperty("screeningId"));
        int expectedBookingId = Integer.parseInt(props.getProperty("bookingId"));
        String expectedBookingCode = props.getProperty("bookingCode");
        List<String> expectedTickets = List.of(props.getProperty("tickets").split(";"));
        int expectedPaymentId = Integer.parseInt(props.getProperty("paymentId"));
        BigDecimal expectedPaymentAmount = new BigDecimal(props.getProperty("paymentAmount"));
        BigDecimal expectedRevTotal = new BigDecimal(props.getProperty("revReportTotal"));
        int expectedRevBookings = Integer.parseInt(props.getProperty("revReportBookings"));
        BigDecimal expectedSalesTotal = new BigDecimal(props.getProperty("salesReportTotal"));
        int expectedSalesTickets = Integer.parseInt(props.getProperty("salesReportTickets"));

        System.out.println("=== [PERSISTENCE-TEST] SESSION 2: REOPEN APPLICATION NORMALLY & VERIFY PERSISTENCE ===");
        final AppShell[] s2Holder = new AppShell[1];
        SwingUtilities.invokeAndWait(() -> {
            AppShell s2 = new AppShell(testConfig);
            s2.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            s2.setSize(1420, 880);
            s2.setVisible(true);
            s2Holder[0] = s2;
        });
        AppShell shell2 = s2Holder[0];
        try {
            Thread.sleep(300);

            final BigDecimal[] revReportTotalAfter = new BigDecimal[1];
            final int[] revReportBookingsAfter = new int[1];
            final BigDecimal[] salesReportTotalAfter = new BigDecimal[1];
            final int[] salesReportTicketsAfter = new int[1];

            SwingUtilities.invokeAndWait(() -> {
                // Đối chiếu Màn hình 1 sau khi mở lại
                shell2.showScreen(AppShell.TAB_SCREENING);
                shell2.getScreeningPanel().filterByDateForVerification(TEST_SCREENING_DATE);
                List<ScreeningDetailView> scrAfter = shell2.getScreeningPanel().getCurrentRows();
                if (scrAfter.size() != 1 || scrAfter.getFirst().screeningId() != expectedScreeningId) {
                    throw new IllegalStateException("Phiên 2: Suất chiếu #" + expectedScreeningId + " không còn khớp sau khi mở lại ứng dụng!");
                }
                if (scrAfter.getFirst().soldSeats() != 2) {
                    throw new IllegalStateException("Phiên 2: Số ghế đã bán trên suất chiếu phải bằng 2, thực tế: " + scrAfter.getFirst().soldSeats());
                }

                // Đối chiếu Màn hình 2 sau khi mở lại
                shell2.showScreen(AppShell.TAB_BOOKING);
                if (!shell2.getBookingPanel().selectScreeningById(expectedScreeningId)) {
                    throw new IllegalStateException("Phiên 2: Không chọn được suất chiếu #" + expectedScreeningId);
                }
                List<BookingSummaryView> bkAfter = shell2.getBookingPanel().getRecentBookings();
                if (bkAfter.size() != 1 || bkAfter.getFirst().bookingId() != expectedBookingId
                        || bkAfter.getFirst().totalAmount().compareTo(expectedPaymentAmount) != 0) {
                    throw new IllegalStateException("Phiên 2: Đơn đặt vé #" + expectedBookingId + " bị sai lệch sau khi mở lại!");
                }

                // Đối chiếu Màn hình 4 (Báo cáo Doanh thu) sau khi mở lại
                shell2.showScreen(AppShell.TAB_REVENUE_REPORT);
                shell2.getRevenueReportPanel().setDateRangeForVerification(todayIso, todayIso);
                shell2.getRevenueReportPanel().refreshPreview();
                RevenueReportData revAfter = shell2.getRevenueReportPanel().getCurrentReportData();
                revReportTotalAfter[0] = revAfter.totalPaidRevenue();
                revReportBookingsAfter[0] = revAfter.totalPaidBookings();

                // Đối chiếu Màn hình 5 (Báo cáo Bán vé) sau khi mở lại
                shell2.showScreen(AppShell.TAB_SALES_REPORT);
                shell2.getSalesReportPanel().setDateRangeForVerification(TEST_SCREENING_DATE, TEST_SCREENING_DATE);
                shell2.getSalesReportPanel().refreshPreview();
                TicketSalesReportData salesAfter = shell2.getSalesReportPanel().getCurrentReportData();
                salesReportTotalAfter[0] = salesAfter.totalSalesRevenue();
                salesReportTicketsAfter[0] = salesAfter.totalTicketsSold();
            });

            List<String> recordedTicketsAfter = new ArrayList<>();
            int paymentIdAfter;
            BigDecimal paymentAmountAfter;
            try (Connection conn = cp.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ticket_id, seat_id, unit_price, ticket_status FROM ticket WHERE booking_id = ? ORDER BY ticket_id ASC")) {
                    ps.setInt(1, expectedBookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            recordedTicketsAfter.add(String.format("ticket_id=%d(seat=%d,price=%s,status=%s)",
                                    rs.getInt("ticket_id"),
                                    rs.getInt("seat_id"),
                                    rs.getBigDecimal("unit_price").toPlainString(),
                                    rs.getString("ticket_status")));
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT payment_id, amount FROM payment WHERE booking_id = ?")) {
                    ps.setInt(1, expectedBookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Phiên 2: Payment biến mất sau khi mở lại!");
                        }
                        paymentIdAfter = rs.getInt("payment_id");
                        paymentAmountAfter = rs.getBigDecimal("amount");
                    }
                }
            }

            System.out.printf("[AFTER-REOPEN] screening_id=%d | booking_id=%d (%s) | tickets=%s | payment_id=%d (amount=%s)%n",
                    expectedScreeningId, expectedBookingId, expectedBookingCode,
                    recordedTicketsAfter, paymentIdAfter, paymentAmountAfter.toPlainString());
            System.out.printf("[AFTER-REOPEN-REPORTS] RevenueReport(%s)=%s (%d bookings) | SalesReport(%s)=%s (%d tickets)%n",
                    todayIso, revReportTotalAfter[0].toPlainString(), revReportBookingsAfter[0],
                    TEST_SCREENING_DATE, salesReportTotalAfter[0].toPlainString(), salesReportTicketsAfter[0]);

            if (!expectedTickets.equals(recordedTicketsAfter)
                    || expectedPaymentId != paymentIdAfter
                    || expectedPaymentAmount.compareTo(paymentAmountAfter) != 0
                    || expectedRevTotal.compareTo(revReportTotalAfter[0]) != 0
                    || expectedRevBookings != revReportBookingsAfter[0]
                    || expectedSalesTotal.compareTo(salesReportTotalAfter[0]) != 0
                    || expectedSalesTickets != salesReportTicketsAfter[0]) {
                throw new IllegalStateException("Phát hiện sai lệch dữ liệu giữa Trước khi đóng và Sau khi mở lại ứng dụng!");
            }

            System.out.println("[PERSISTENCE-VERIFIED] 100% MATCH BEFORE CLOSE vs AFTER NORMAL REOPEN ACROSS FULL JVM RESTART (ZERO RESET ON STARTUP).");
        } finally {
            SwingUtilities.invokeAndWait(shell2::dispose);
        }
    }

    private static void disposeAllWindowsSafely() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (Frame f : Frame.getFrames()) {
                    f.setVisible(false);
                    f.dispose();
                }
                for (Window w : Window.getWindows()) {
                    w.setVisible(false);
                    w.dispose();
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void cleanUpPersistenceTestDate(ConnectionProvider cp) throws Exception {
        TestDatabaseGuard.verifyLiveTestConnection(cp);
        try (Connection conn = cp.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE pm FROM payment pm
                    JOIN booking bk ON bk.booking_id = pm.booking_id
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = ?
                    """)) {
                ps.setString(1, TEST_SCREENING_DATE);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE tk FROM ticket tk
                    JOIN screening sc ON sc.screening_id = tk.screening_id
                    WHERE DATE(sc.start_time) = ?
                    """)) {
                ps.setString(1, TEST_SCREENING_DATE);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE bk FROM booking bk
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = ?
                    """)) {
                ps.setString(1, TEST_SCREENING_DATE);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM screening WHERE DATE(start_time) = ?")) {
                ps.setString(1, TEST_SCREENING_DATE);
                ps.executeUpdate();
            }
        }
    }
}
