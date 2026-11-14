package com.cinema.app;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.payment.*;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.config.VnpayConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;

/** Separate launcher: never connects sandbox callbacks to the protected demo schema. */
public final class SandboxMain {
    private SandboxMain() {}
    public static void main(String[] args) throws Exception {
        AppConfig app=AppConfig.loadTest();var cp=new ConnectionProvider(app);var config=VnpayConfig.load();
        var payments=new SandboxPaymentService(new SandboxPaymentRepository(cp),new BookingRepository(cp),
                new TransactionTemplate(cp),config,Clock.systemUTC());
        var callback=new VnpayCallbackServer(payments,config.port());
        Theme.setupLookAndFeel();
        SwingUtilities.invokeLater(()->{
            try {
                AppShell shell=new AppShell(app,payments);
                shell.setTitle("CineViet — VNPay Sandbox — CSDL kiểm thử");
                shell.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){callback.close();}});
                shell.setVisible(true);
            }catch(RuntimeException e){callback.close();throw e;}
        });
    }
}
