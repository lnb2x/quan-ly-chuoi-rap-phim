package com.cinema.integration;

import com.cinema.feature.booking.*;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.search.*;
import com.cinema.feature.report.revenue.*;
import com.cinema.feature.report.sales.*;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.db.*;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: mvnw.cmd -Dtest=CinemaDatasetCheck test (after prepare_dataset_sample.py). */
class CinemaDatasetCheck {
    private static final Path DIR = Path.of("verification", "dataset-sample");
    private static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("cinema_branch", "branch_id,branch_code,branch_name,address,city,phone,active"),
            Map.entry("employee", "employee_id,branch_id,employee_code,full_name,phone,role,hired_date"),
            Map.entry("screening_room", "room_id,branch_id,room_name,room_type,active"),
            Map.entry("seat", "seat_id,room_id,seat_row,seat_number,seat_type_code"),
            Map.entry("movie", "movie_id,movie_code,title,duration_minutes,release_date,age_rating,country"),
            Map.entry("screening", "screening_id,movie_id,room_id,start_time,end_time,base_price,status"),
            Map.entry("customer", "customer_id,full_name,phone,email,created_at"),
            Map.entry("booking", "booking_id,booking_code,screening_id,customer_id,employee_id,booked_at,booking_status"),
            Map.entry("ticket", "ticket_id,booking_id,seat_id,unit_price,ticket_status"),
            Map.entry("payment", "payment_id,booking_id,amount,payment_method,payment_status,paid_at,transaction_ref"));
    private static final Map<String, List<Integer>> IDS = new LinkedHashMap<>();
    private static final Properties EXPECTED = new Properties();
    private static ConnectionProvider cp;
    private static ScreeningRepository screenings;
    private static final LocalDate FROM = LocalDate.of(2020, 1, 1), TO = LocalDate.of(2025, 12, 31);

    @BeforeAll
    static void load() throws Exception {
        cp = TestDatabaseGuard.verifyAndCreateTestProvider();
        screenings = new ScreeningRepository(cp);
        try (var reader = Files.newBufferedReader(DIR.resolve("expected.properties"))) { EXPECTED.load(reader); }
        // PK collisions roll back the entire import; cleanup uses only successfully inserted IDs.
        var lines = Files.readAllLines(DIR.resolve("fixture.tsv"));
        try (Connection c = cp.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (String line : lines) {
                    String[] cells = line.split("\t", -1);
                    String table = cells[0], columns = COLUMNS.get(table);
                    if (columns == null || columns.split(",").length != cells.length - 1) {
                        throw new IllegalArgumentException("Fixture không hợp lệ: " + table);
                    }
                    String[] values = Arrays.stream(cells).skip(1)
                            .map(s -> new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8)).toArray(String[]::new);
                    int id = Integer.parseInt(values[0]);
                    if (id < 1_000_000 || id >= 2_000_000) throw new IllegalArgumentException("ID ngoài vùng fixture");
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO " + table + " (" + columns + ") VALUES ("
                            + String.join(",", Collections.nCopies(values.length, "?")) + ")")) {
                        for (int i = 0; i < values.length; i++) p.setString(i + 1, values[i]);
                        p.executeUpdate();
                    }
                    IDS.computeIfAbsent(table, ignored -> new ArrayList<>()).add(id);
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                IDS.clear();
                throw ex;
            }
        }
        System.out.println("[DATASET] Đã nạp " + lines.size() + " dòng; vé=" + EXPECTED.getProperty("tickets"));
    }

    @AfterAll
    static void clean() throws Exception {
        if (IDS.isEmpty()) return;
        try (Connection c = cp.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("UPDATE ticket SET ticket_status='CANCELLED' WHERE ticket_id=?")) {
                    for (int id : IDS.getOrDefault("ticket", List.of())) { p.setInt(1, id); p.addBatch(); }
                    p.executeBatch();
                }
                for (String table : List.of("payment", "ticket", "booking", "screening", "seat", "employee", "screening_room", "customer", "movie", "cinema_branch")) {
                    String pk = COLUMNS.get(table).split(",")[0];
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM " + table + " WHERE " + pk + "=?")) {
                        for (int id : IDS.getOrDefault(table, List.of())) { p.setInt(1, id); p.addBatch(); }
                        p.executeBatch();
                    }
                }
                c.commit();
                System.out.println("[DATASET] Đã dọn toàn bộ dòng fixture.");
            } catch (Exception ex) { c.rollback(); throw ex; }
        }
    }

    @Test
    void reportsMatchSourceTicketLedgerForEveryBranch() {
        var revenue = new RevenueReportService(new RevenueReportRepository(cp), screenings, new PdfReportWriter());
        var sales = new TicketSalesReportService(new TicketSalesReportRepository(cp), screenings, new PdfReportWriter());
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int branch : IDS.get("cinema_branch")) {
            if (!EXPECTED.containsKey("branch." + branch + ".revenue")) continue;
            var r = revenue.generateReport(FROM, TO, branch, "Dataset 1K");
            var s = sales.generateReport(FROM, TO, branch, "Dataset 1K", null, null);
            BigDecimal expected = new BigDecimal(EXPECTED.getProperty("branch." + branch + ".revenue"));
            int tickets = Integer.parseInt(EXPECTED.getProperty("branch." + branch + ".tickets"));
            assertEquals(0, expected.compareTo(r.totalPaidRevenue()));
            assertEquals(0, expected.compareTo(s.totalSalesRevenue()));
            assertEquals(tickets, r.totalValidTickets());
            assertEquals(tickets, s.totalTicketsSold());
            assertEquals(Integer.parseInt(EXPECTED.getProperty("branch." + branch + ".bookings")), r.totalPaidBookings());
            sum = sum.add(r.totalPaidRevenue()); count += tickets;
        }
        assertEquals(Integer.parseInt(EXPECTED.getProperty("tickets")), count);
        assertEquals(0, new BigDecimal(EXPECTED.getProperty("revenue")).compareTo(sum));
        System.out.println("[DATASET] Đối chiếu doanh thu nguồn=" + sum + "; vé=" + count);
    }

    @Test
    void searchPaginationAndAvailabilityCoverEverySourceShow() {
        var search = new ScreeningSearchService(new ScreeningSearchRepository(cp), screenings);
        Set<Integer> found = new HashSet<>();
        int sold = 0, capacity = 0;
        var first = search.searchPage("DS1K ", null, null, null, null, null, false, 1, 4);
        assertEquals(Integer.parseInt(EXPECTED.getProperty("screenings")), first.page().totalCount());
        for (int page = 1; page <= first.page().totalPages(); page++) {
            var result = search.searchPage("DS1K ", null, null, null, null, null, false, page, 4);
            assertEquals(Integer.parseInt(EXPECTED.getProperty("tickets")), result.totalSoldSeats());
            for (var s : result.page().items()) {
                assertTrue(found.add(s.screeningId()), "Suất bị lặp giữa các trang");
                assertEquals(s.totalSeats() - s.soldSeats(), s.availableSeats());
                sold += s.soldSeats(); capacity += s.totalSeats();
            }
        }
        assertEquals(new HashSet<>(IDS.get("screening")), found);
        assertEquals(Integer.parseInt(EXPECTED.getProperty("tickets")), sold);
        assertEquals(Integer.parseInt(EXPECTED.getProperty("capacity")), capacity);
    }

    @Test
    void importedOccupiedSeatIsRejectedByApplicationAndDatabase() throws Exception {
        int seat, show, employee;
        try (Connection c = cp.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT t.booking_id,t.seat_id,b.screening_id,b.employee_id FROM ticket t JOIN booking b ON b.booking_id=t.booking_id WHERE t.ticket_id=?")) {
            p.setInt(1, IDS.get("ticket").getFirst());
            try (var r = p.executeQuery()) { assertTrue(r.next()); seat=r.getInt(2); show=r.getInt(3); employee=r.getInt(4); }
        }
        var bookingService = new BookingService(new BookingRepository(cp), new TransactionTemplate(cp));
        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(show, null, employee, List.of(seat), "CASH", "SUCCESS"));
        // A different order bypasses uq_ticket_booking_seat and exercises the seat-lock PK.
        try (Connection c = cp.getConnection()) {
            c.setAutoCommit(false);
            try {
                int other;
                try (PreparedStatement p = c.prepareStatement("INSERT INTO booking (booking_code,screening_id,employee_id) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, "DS1K-CHECK-" + UUID.randomUUID().toString().substring(0, 8)); p.setInt(2, show); p.setInt(3, employee); p.executeUpdate();
                    try (var keys = p.getGeneratedKeys()) { assertTrue(keys.next()); other = keys.getInt(1); }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO ticket (booking_id,seat_id,unit_price) VALUES (?,?,1000)")) {
                    p.setInt(1, other); p.setInt(2, seat);
                    SQLException ex = assertThrows(SQLException.class, p::executeUpdate);
                    assertEquals(1062, ex.getErrorCode());
                    assertTrue(ex.getMessage().contains("screening_seat_lock"));
                }
            } finally { c.rollback(); }
        }
    }
}
