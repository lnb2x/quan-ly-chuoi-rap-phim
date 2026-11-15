package com.cinema.feature.payment;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.shared.config.VnpayConfig;
import com.cinema.shared.db.TransactionTemplate;
import java.net.URI;
import java.time.*;
import java.util.List;
import java.util.regex.Pattern;

public final class SandboxPaymentService {
    public record Checkout(int bookingId,URI url) {}
    private static final Pattern REFERENCE=Pattern.compile("VP([1-9][0-9]{0,9})P([1-9][0-9]{0,9})");
    private final SandboxPaymentRepository repository;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final TransactionTemplate tx;
    private final VnpayConfig config;
    private final VnpayGateway gateway;
    private final Clock clock;

    public SandboxPaymentService(SandboxPaymentRepository repository,BookingRepository bookings,
                                 TransactionTemplate tx,VnpayConfig config,Clock clock) {
        repository.assertTestDatabase();this.repository=repository;this.bookings=bookings;this.tx=tx;
        this.config=config;this.gateway=new VnpayGateway(config);this.clock=clock;
        this.bookingService=new BookingService(bookings,tx,clock);
    }

    public Checkout create(int screeningId,Integer customerId,int employeeId,List<Integer> seats) {
        config.requireCredentials();
        int id=bookingService.createSandboxBooking(screeningId,customerId,employeeId,seats);
        try {
            return tx.execute(c->{
                var payment=repository.lock(c,id,0);
                return new Checkout(id,gateway.checkout("VP"+id+"P"+payment.paymentId(),payment.amount(),payment.created()));
            });
        } catch(RuntimeException e) {bookingService.cancelBooking(id);throw e;}
    }

    public String ipn(String query) {
        final java.util.Map<String,String> data;
        try{data=gateway.verify(query);}catch(IllegalArgumentException e){return "97";}
        var matcher=REFERENCE.matcher(data.getOrDefault("vnp_TxnRef",""));
        if(!matcher.matches() || !data.getOrDefault("vnp_Amount","").matches("[0-9]{1,12}")
                || !data.getOrDefault("vnp_ResponseCode","").matches("[0-9]{2}")
                || !data.getOrDefault("vnp_TransactionStatus","").matches("[0-9]{2}"))return "99";
        boolean success="00".equals(data.get("vnp_ResponseCode")) && "00".equals(data.get("vnp_TransactionStatus"));
        if(!java.util.Set.of("00","02").contains(data.get("vnp_TransactionStatus"))
                || ("00".equals(data.get("vnp_TransactionStatus")) && !success))return "99";
        String providerId=data.getOrDefault("vnp_TransactionNo","0");
        if(!providerId.matches("[0-9]{1,15}") || (success && new java.math.BigInteger(providerId).signum()==0))return "99";
        int bookingId,paymentId;
        try{bookingId=Integer.parseInt(matcher.group(1));paymentId=Integer.parseInt(matcher.group(2));}
        catch(NumberFormatException e){return "01";}
        return tx.execute(c->{
            var p=repository.lock(c,bookingId,paymentId);
            if(p==null || p.reference()==null || !p.reference().startsWith("VNPAY-"))return "01";
            if(!VnpayGateway.scaled(p.amount()).equals(new java.math.BigInteger(data.get("vnp_Amount")).toString()))return "04";
            if("SUCCESS".equals(p.paymentStatus()))return p.reference().equals("VNPAY-SUCCESS-"+providerId) ? "02" : "99";
            if(p.reference().startsWith("VNPAY-LATE-"))return p.reference().equals("VNPAY-LATE-"+providerId) ? "02" : "99";
            if("CANCELLED".equals(p.bookingStatus()) || !now().isBefore(p.created().plusMinutes(15))) {
                if("PENDING".equals(p.bookingStatus()))bookings.cancelBookingRecords(c,bookingId);
                if(success)repository.reference(c,paymentId,"VNPAY-LATE-"+providerId);
                return "00";
            }
            if(!"PENDING".equals(p.bookingStatus()) || !"PENDING".equals(p.paymentStatus()))return "02";
            bookings.transitionPendingPayment(c,bookingId,success ? "SUCCESS" : "FAILED");
            repository.reference(c,paymentId,"VNPAY-"+(success ? "SUCCESS-" : "FAILED-")+providerId);
            return "00";
        });
    }

    public void expirePending() throws java.sql.SQLException {
        for(int id:repository.expired(now().minusMinutes(15)))tx.execute(c->{
            var p=repository.lock(c,id,0);
            if(p!=null && "PENDING".equals(p.bookingStatus()) && "PENDING".equals(p.paymentStatus())
                    && p.reference().startsWith("VNPAY-") && !now().isBefore(p.created().plusMinutes(15)))
                bookings.cancelBookingRecords(c,id);
            return null;
        });
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh"));}
}
