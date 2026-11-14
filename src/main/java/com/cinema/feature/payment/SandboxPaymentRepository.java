package com.cinema.feature.payment;

import com.cinema.shared.db.ConnectionProvider;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SandboxPaymentRepository {
    public record Payment(int bookingId,int paymentId,BigDecimal amount,String bookingStatus,String paymentStatus,
                          String reference,LocalDateTime created) {}
    private final ConnectionProvider cp;
    public SandboxPaymentRepository(ConnectionProvider cp) {this.cp=cp;}

    public void assertTestDatabase() {
        try(var c=cp.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),CURRENT_USER(),VERSION(),@@version_comment")) {
            if(!r.next() || !"cinema_chain_test".equals(r.getString(1)) || !r.getString(2).startsWith("cinema_app@")
                    || !r.getString(3).startsWith("8.4.") || !r.getString(4).contains("MySQL"))
                throw new SecurityException("VNPay Sandbox chỉ được chạy bằng cinema_app trên cinema_chain_test.");
        }catch(SQLException e){throw new IllegalStateException("Không xác minh được DB sandbox.",e);}
    }

    public Payment lock(Connection c,int bookingId,int paymentId) throws SQLException {
        try(var p=c.prepareStatement("SELECT booking_status,booked_at FROM booking WHERE booking_id=? FOR UPDATE")) {
            p.setInt(1,bookingId);
            try(var r=p.executeQuery()) {
                if(!r.next())return null;
                String status=r.getString(1); var created=r.getTimestamp(2).toLocalDateTime();
                try(var q=c.prepareStatement("SELECT payment_id,amount,payment_status,transaction_ref FROM payment WHERE booking_id=? AND (?=0 OR payment_id=?) ORDER BY payment_id DESC FOR UPDATE")) {
                    q.setInt(1,bookingId);q.setInt(2,paymentId);q.setInt(3,paymentId);
                    try(var t=q.executeQuery()) {
                        if(!t.next())return null;
                        return new Payment(bookingId,t.getInt(1),t.getBigDecimal(2),status,t.getString(3),t.getString(4),created);
                    }
                }
            }
        }
    }

    public List<Integer> expired(LocalDateTime cutoff) throws SQLException {
        try(var c=cp.getConnection();var p=c.prepareStatement("SELECT DISTINCT b.booking_id FROM booking b JOIN payment p ON p.booking_id=b.booking_id WHERE b.booking_status='PENDING' AND p.payment_status='PENDING' AND p.transaction_ref LIKE 'VNPAY-%' AND b.booked_at<=?")) {
            p.setTimestamp(1,Timestamp.valueOf(cutoff));var result=new ArrayList<Integer>();
            try(var r=p.executeQuery()){while(r.next())result.add(r.getInt(1));}return result;
        }
    }

    public void reference(Connection c,int paymentId,String reference) throws SQLException {
        try(var p=c.prepareStatement("UPDATE payment SET transaction_ref=? WHERE payment_id=?")) {
            p.setString(1,reference);p.setInt(2,paymentId);p.executeUpdate();
        }
    }
}
