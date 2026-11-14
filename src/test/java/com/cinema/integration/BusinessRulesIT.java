package com.cinema.integration;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.feature.report.revenue.RevenueReportRepository;
import com.cinema.feature.report.revenue.RevenueReportService;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Fixtures are isolated branches; no seed rows or protected database are edited. */
class BusinessRulesIT {
    protected static ConnectionProvider cp;
    protected static TransactionTemplate tx;
    protected static BookingRepository bookings;
    protected static BookingService booking;
    protected static ScreeningService screenings;
    protected int branch, room, employee, standardSeat, vipSeat, screening;
    protected final LocalDateTime start = LocalDateTime.of(2027, 3, 1, 22, 0);

    @BeforeAll static void init() {
        cp = TestDatabaseGuard.verifyAndCreateTestProvider(); tx = new TransactionTemplate(cp);
        bookings = new BookingRepository(cp); booking = new BookingService(bookings, tx);
        screenings = new ScreeningService(new ScreeningRepository(cp), tx);
    }

    @BeforeEach void setup() throws Exception {
        String code = UUID.randomUUID().toString().substring(0, 12);
        try (var c = cp.getConnection()) {
            c.setAutoCommit(false);
            try {
                branch = insert(c, "INSERT INTO cinema_branch(branch_code,branch_name,address,city,phone) VALUES (?,?,?,?,?)", code, code, "Địa chỉ kiểm thử", "Hà Nội", code);
                room = insert(c, "INSERT INTO screening_room(branch_id,room_name) VALUES (?,?)", branch, "Phòng kiểm thử");
                employee = insert(c, "INSERT INTO employee(branch_id,employee_code,full_name,phone,hired_date) VALUES (?,?,?,?,?)", branch, code, "Nhân viên kiểm thử", code, "2020-01-01");
                standardSeat = insert(c, "INSERT INTO seat(room_id,seat_row,seat_number,seat_type_code) VALUES (?,'A',1,'STD')", room);
                vipSeat = insert(c, "INSERT INTO seat(room_id,seat_row,seat_number,seat_type_code) VALUES (?,'A',2,'VIP')", room);
                c.commit();
            } catch (Exception e) { c.rollback(); branch=0; throw e; }
        }
        screening = screenings.createScreening(1, room, start, start.plusHours(4), new BigDecimal("100000"));
    }

    @AfterEach void cleanup() {
        if (branch == 0) return;
        tx.execute(c -> {
            exec(c,"UPDATE ticket t JOIN booking b ON b.booking_id=t.booking_id JOIN screening s ON s.screening_id=b.screening_id JOIN screening_room r ON r.room_id=s.room_id SET t.ticket_status='CANCELLED' WHERE r.branch_id=?",branch);
            exec(c,"DELETE p FROM payment p JOIN booking b ON b.booking_id=p.booking_id JOIN screening s ON s.screening_id=b.screening_id JOIN screening_room r ON r.room_id=s.room_id WHERE r.branch_id=?",branch);
            exec(c,"DELETE t FROM ticket t JOIN booking b ON b.booking_id=t.booking_id JOIN screening s ON s.screening_id=b.screening_id JOIN screening_room r ON r.room_id=s.room_id WHERE r.branch_id=?",branch);
            exec(c,"DELETE b FROM booking b JOIN screening s ON s.screening_id=b.screening_id JOIN screening_room r ON r.room_id=s.room_id WHERE r.branch_id=?",branch);
            exec(c,"DELETE s FROM screening s JOIN screening_room r ON r.room_id=s.room_id WHERE r.branch_id=?",branch);
            exec(c,"DELETE s FROM seat s JOIN screening_room r ON r.room_id=s.room_id WHERE r.branch_id=?",branch);
            exec(c,"DELETE FROM employee WHERE branch_id=?",branch);
            exec(c,"DELETE FROM screening_room WHERE branch_id=?",branch);
            exec(c,"DELETE FROM cinema_branch WHERE branch_id=?",branch);
            return null;
        });
    }

    @Test void closedBranchCannotReceiveNewScreening() throws Exception {
        sql("UPDATE cinema_branch SET active=0 WHERE branch_id=?",branch);
        assertThrows(BusinessRuleException.class, () -> screenings.createScreening(1,room,start.plusDays(1),start.plusDays(1).plusHours(4),BigDecimal.TEN));
    }

    @Test void exactDurationAndOneSecondTooShortHaveDifferentOutcomes() throws Exception {
        long duration = scalar("SELECT duration_minutes FROM movie WHERE movie_id=?",1);
        var next = start.plusDays(1);
        assertThrows(BusinessRuleException.class, () -> screenings.createScreening(1,room,next,next.plusMinutes(duration).minusSeconds(1),BigDecimal.TEN));
        assertTrue(screenings.createScreening(1,room,next,next.plusMinutes(duration),BigDecimal.TEN)>0);
    }

    @Test void adjacentOvernightShowsAreAllowedButContainedOverlapIsRejected() {
        assertTrue(screenings.createScreening(1,room,start.plusHours(4),start.plusHours(8),BigDecimal.TEN)>0);
        assertThrows(BusinessRuleException.class, () -> screenings.createScreening(1,room,start.plusMinutes(1),start.plusHours(4).minusMinutes(1),BigDecimal.TEN));
    }

    @Test void cancelledShowCannotBeBookedAndPendingTicketsPreventCancellation() {
        int id = buy("PENDING",List.of(standardSeat));
        assertThrows(BusinessRuleException.class, () -> screenings.updateScreening(screening,1,room,start,start.plusHours(4),BigDecimal.TEN,"CANCELLED"));
        booking.cancelBooking(id);
        screenings.updateScreening(screening,1,room,start,start.plusHours(4),BigDecimal.TEN,"CANCELLED");
        assertThrows(BusinessRuleException.class, () -> buy("SUCCESS",List.of(standardSeat)));
    }

    @Test void nullSeatAndMissingCustomerAreRejectedWithoutPartialOrder() throws Exception {
        assertThrows(BusinessRuleException.class, () -> buy("SUCCESS",Arrays.asList(standardSeat,null)));
        assertThrows(BusinessRuleException.class, () -> booking.createBooking(screening,Integer.MAX_VALUE,employee,List.of(standardSeat),"CASH","SUCCESS"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?",screening));
        assertEquals(0,scalar("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?",screening));
    }

    @Test void mixedRoomSeatsRejectWholeOrder() throws Exception {
        assertThrows(BusinessRuleException.class, () -> buy("SUCCESS",List.of(standardSeat,1)));
        assertEquals(0,scalar("SELECT COUNT(*) FROM booking WHERE screening_id=?",screening));
    }

    @Test void fullRoomHasNoAvailableSeatAndCancellationRestoresBoth() throws Exception {
        int id = buy("SUCCESS",List.of(standardSeat,vipSeat));
        assertEquals(0,booking.getSeatMap(screening).stream().filter(s -> !s.occupied()).count());
        booking.cancelBooking(id);
        assertEquals(2,booking.getSeatMap(screening).stream().filter(s -> !s.occupied()).count());
        assertEquals(2,scalar("SELECT COUNT(*) FROM ticket WHERE booking_id=? AND ticket_status='CANCELLED' AND cancelled_at IS NOT NULL",id));
    }

    @Test void cancelledOrderCannotReceiveNewValidTicketViaSql() throws Exception {
        int id = buy("PENDING",List.of(standardSeat)); booking.cancelBooking(id);
        try (var c=cp.getConnection()) {
            assertThrows(SQLException.class, () -> bookings.insertTicket(c,id,vipSeat,BigDecimal.TEN,"VALID"));
        }
    }

    @Test void cancelledOrderCannotResurrectTicketViaSql() throws Exception {
        int id = buy("PENDING",List.of(standardSeat)); booking.cancelBooking(id);
        try (var c=cp.getConnection()) {
            assertThrows(SQLException.class, () -> exec(c,"UPDATE ticket SET ticket_status='VALID' WHERE booking_id=?",id));
        }
    }

    @Test void issuedTicketPriceCannotBeRewrittenViaSql() throws Exception {
        int id=buy("SUCCESS",List.of(standardSeat));
        try (var c=cp.getConnection()) {
            assertThrows(SQLException.class, () -> exec(c,"UPDATE ticket SET unit_price=1 WHERE booking_id=?",id));
        }
    }

    @Test void closedBranchKeepsHistoricalRevenueAndDailyTotalsAgree() throws Exception {
        buy("SUCCESS",List.of(standardSeat,vipSeat));
        sql("UPDATE cinema_branch SET active=0 WHERE branch_id=?",branch);
        var report = revenue();
        assertTrue(new RevenueReportService(new RevenueReportRepository(cp),new ScreeningRepository(cp),new PdfReportWriter())
                .listBranches().stream().anyMatch(b -> b.branchId()==branch));
        assertEquals(0,new BigDecimal("220000").compareTo(report.totalPaidRevenue()));
        assertEquals(0,report.totalPaidRevenue().compareTo(report.dailyDetails().stream().map(r->r.paidRevenue()).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }

    @Test void pendingFailedAndRefundedDoNotCountAndHistoricalPricesStayFixed() throws Exception {
        int id=buy("PENDING",List.of(standardSeat,vipSeat));
        assertEquals(0,revenue().totalPaidRevenue().signum());
        screenings.updateScreening(screening,1,room,start,start.plusHours(4),new BigDecimal("200000"),"OPEN");
        booking.transitionPendingPayment(id,"SUCCESS");
        assertEquals(0,new BigDecimal("220000").compareTo(revenue().totalPaidRevenue()));
        booking.cancelBooking(id);
        assertEquals(0,revenue().totalPaidRevenue().signum());
        int resold=buy("PENDING",List.of(standardSeat)); booking.transitionPendingPayment(resold,"FAILED");
        assertEquals(0,revenue().totalPaidRevenue().signum());
    }

    @Test void failedPaymentAttemptsDoNotMultiplyRevenue() throws Exception {
        int id=buy("SUCCESS",List.of(standardSeat,vipSeat));
        try(var c=cp.getConnection()) {
            for(int i=0;i<3;i++) bookings.insertPayment(c,id,new BigDecimal("220000"),"CARD","FAILED",null,"FAILED-"+i);
        }
        assertEquals(0,new BigDecimal("220000").compareTo(revenue().totalPaidRevenue()));
        assertEquals(2,revenue().totalValidTickets());
    }

    @Test void sqlCancellationAtomicallyRefundsAndReleasesSeats() throws Exception {
        int id=buy("SUCCESS",List.of(standardSeat,vipSeat));
        sql("UPDATE booking SET booking_status='CANCELLED' WHERE booking_id=?",id);
        assertEquals(0,scalar("SELECT COUNT(*) FROM ticket WHERE booking_id=? AND ticket_status='VALID'",id));
        assertEquals(1,scalar("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='REFUNDED'",id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?",screening));
    }

    @Test void sqlCannotConfirmPendingOrderWithoutSuccessfulPayment() throws Exception {
        int id=buy("PENDING",List.of(standardSeat));
        try(var c=cp.getConnection()) {
            assertThrows(SQLException.class,()->exec(c,"UPDATE booking SET booking_status='CONFIRMED' WHERE booking_id=?",id));
        }
    }

    @Test void paymentAndCancellationRaceEndsCancelledWithoutMoneyOrSeatLeak() throws Exception {
        int id=buy("PENDING",List.of(standardSeat,vipSeat));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var pay=pool.submit(()->{gate.await();try{booking.transitionPendingPayment(id,"SUCCESS");}catch(BusinessRuleException expected){} return null;});
            var cancel=pool.submit(()->{gate.await();booking.cancelBooking(id);return null;});
            gate.countDown(); pay.get(10,TimeUnit.SECONDS); cancel.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,scalar("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CANCELLED'",id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'",id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?",screening));
        assertEquals(0,revenue().totalPaidRevenue().signum());
    }

    @Test void directSqlConcurrentScreeningsCannotBypassOverlapUsingOldSnapshots() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<2;i++)results.add(pool.submit(()->{
                try(var c=cp.getConnection()) {
                    c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
                    try {
                        try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM screening")){r.next();}
                        ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout barrier");
                        insert(c,"INSERT INTO screening(movie_id,room_id,start_time,end_time,base_price) VALUES (1,?,?,?,100000)",room,start.plusDays(2),start.plusDays(2).plusHours(4));
                        c.commit();return true;
                    }catch(SQLException e){
                        if(!"45000".equals(e.getSQLState()) && e.getErrorCode()!=1213)throw e;
                        return false;
                    }finally{c.rollback();}
                }
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();int winners=0;
            for(var result:results)if(result.get(15,TimeUnit.SECONDS))winners++;
            assertEquals(1,winners,"SQL trực tiếp không được tạo hai lịch chồng lấn dù transaction có snapshot cũ");
        }
    }

    protected int buy(String status,List<Integer> seats) { return booking.createBooking(screening,null,employee,seats,"CASH",status); }
    protected com.cinema.domain.RevenueReportData revenue() {
        return new RevenueReportService(new RevenueReportRepository(cp),new ScreeningRepository(cp),new PdfReportWriter())
                .generateReport(LocalDate.of(2020,1,1),LocalDate.of(2028,12,31),branch,"Kiểm thử");
    }
    protected static int insert(Connection c,String sql,Object... values) throws SQLException {
        try(var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]); p.executeUpdate();
            try(var r=p.getGeneratedKeys()){assertTrue(r.next());return r.getInt(1);}
        }
    }
    protected static void exec(Connection c,String sql,Object... values) throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}
    }
    protected static void sql(String sql,Object... values) throws Exception {try(var c=cp.getConnection()){exec(c,sql,values);}}
    protected static long scalar(String sql,Object... values) throws Exception {
        try(var c=cp.getConnection();var p=c.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);try(var r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}
        }
    }
}
