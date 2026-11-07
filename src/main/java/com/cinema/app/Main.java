package com.cinema.app;

import com.cinema.shared.config.AppConfig;
import com.cinema.shared.ui.Theme;

import javax.swing.*;

/**
 * Điểm khởi chạy chính của Ứng dụng Desktop Hệ thống Quản lý Chuỗi Rạp Chiếu Phim.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Theme.setupLookAndFeel();
        AppConfig config = AppConfig.loadProduction();
        SwingUtilities.invokeLater(() -> {
            AppShell shell = new AppShell(config);
            shell.setVisible(true);
        });
    }
}
