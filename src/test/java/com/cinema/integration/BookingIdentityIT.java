package com.cinema.integration;

import com.cinema.feature.booking.BookingService;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BookingIdentityIT {
    private final BusinessRulesIT fixture = new BusinessRulesIT();
    private static final Instant SAME_INSTANT = Instant.parse("2026-11-15T23:58:00Z");

    @BeforeAll static void init() { BusinessRulesIT.init(); }
    @BeforeEach void setup() throws Exception { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test
    void independentJvmsCanBookDifferentSeatsAtSameInstantWithVietnamDates() throws Exception {
        List<Process> children = new ArrayList<>();
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        try {
            for (int seat : List.of(fixture.standardSeat, fixture.vipSeat)) {
                children.add(new ProcessBuilder(java, "-Duser.timezone=UTC", "-cp", System.getProperty("java.class.path"),
                        BookingIdentityIT.class.getName(), Integer.toString(fixture.screening),
                        Integer.toString(fixture.employee), Integer.toString(seat)).redirectErrorStream(true).start());
            }
            for (Process child : children) {
                assertTrue(child.waitFor(20, TimeUnit.SECONDS), "JVM đặt vé phải kết thúc đúng hạn");
                assertEquals(0, child.exitValue(), new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }
            try (var c = BusinessRulesIT.cp.getConnection(); var p = c.prepareStatement(
                    "SELECT COUNT(*),COUNT(DISTINCT booking_code),MAX(CHAR_LENGTH(booking_code)),MIN(booked_at),MAX(booked_at) FROM booking WHERE screening_id=?")) {
                p.setInt(1, fixture.screening);
                try (var r = p.executeQuery()) {
                    assertTrue(r.next());
                    assertEquals(2, r.getInt(1));
                    assertEquals(2, r.getInt(2));
                    assertTrue(r.getInt(3) <= 30, "Mã đơn phải vừa VARCHAR(30)");
                    LocalDateTime vietnamTime = LocalDateTime.of(2026, 11, 16, 6, 58);
                    assertEquals(vietnamTime, r.getTimestamp(4).toLocalDateTime());
                    assertEquals(vietnamTime, r.getTimestamp(5).toLocalDateTime());
                }
            }
        } finally {
            for (Process child : children) {
                if (child.isAlive()) {
                    child.destroyForcibly();
                    child.waitFor(10, TimeUnit.SECONDS);
                }
            }
        }
    }

    public static void main(String[] args) {
        BusinessRulesIT.init();
        BookingService service = new BookingService(BusinessRulesIT.bookings, BusinessRulesIT.tx,
                Clock.fixed(SAME_INSTANT, ZoneOffset.UTC));
        service.createBooking(Integer.parseInt(args[0]), null, Integer.parseInt(args[1]),
                List.of(Integer.parseInt(args[2])), "CASH", "SUCCESS");
    }
}
