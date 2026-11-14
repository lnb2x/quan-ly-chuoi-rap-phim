package com.cinema.feature.payment;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Loopback only. Public sandbox IPN needs an operator-configured HTTPS forward. */
public final class VnpayCallbackServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService requests=Executors.newFixedThreadPool(4,r->{var t=new Thread(r,"vnpay-ipn");t.setDaemon(true);return t;});
    private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"vnpay-expiry");t.setDaemon(true);return t;});

    public VnpayCallbackServer(SandboxPaymentService service,int port) throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),16);
        server.setExecutor(requests);
        server.createContext("/sandbox/ipn",exchange->{
            if(!"GET".equals(exchange.getRequestMethod()) || !"/sandbox/ipn".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(405,-1);exchange.close();return;
            }
            String code;
            try{code=service.ipn(exchange.getRequestURI().getRawQuery());}
            catch(RuntimeException e){code="99";System.err.println("[VNPAY-SANDBOX] IPN chưa được xử lý; sẽ cần retry/đối soát.");}
            byte[] response=("{\"RspCode\":\""+code+"\",\"Message\":\"Sandbox IPN\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.sendResponseHeaders(200,response.length);
            try(var out=exchange.getResponseBody()){out.write(response);}finally{exchange.close();}
        });
        server.createContext("/sandbox/return",exchange->{
            // Browser navigation never authorizes a payment, even with a signed query.
            byte[] response=("<!doctype html><html lang='vi'><meta charset='utf-8'><title>VNPay Sandbox</title>"
                    +"<h1>Đã quay lại từ cổng thanh toán</h1><p>Đây là môi trường thử nghiệm. "
                    +"Quay lại ứng dụng và làm mới danh sách đơn để xem kết quả đã được IPN xác minh.</p></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'none'");
            exchange.sendResponseHeaders(200,response.length);
            try(var out=exchange.getResponseBody()){out.write(response);}finally{exchange.close();}
        });
        expiry.scheduleWithFixedDelay(()->{
            try{service.expirePending();}catch(Exception e){System.err.println("[VNPAY-SANDBOX] Chưa giải phóng được đơn hết hạn; sẽ thử lại.");}
        },0,30,TimeUnit.SECONDS);
        server.start();
    }
    public int port(){return server.getAddress().getPort();}
    @Override public void close(){server.stop(0);requests.shutdownNow();expiry.shutdownNow();}
}
