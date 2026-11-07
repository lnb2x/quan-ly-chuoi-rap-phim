package com.cinema.shared.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Thanh thông báo trạng thái (Đang tải, Thành công, Trống, Cảnh báo, Lỗi)
 * hiển thị bằng tiếng Việt rõ ràng trên từng màn hình.
 */
public final class StatusBanner extends JPanel {

    public enum StatusType {
        INFO, SUCCESS, WARNING, ERROR
    }

    private final JLabel messageLabel;

    public StatusBanner() {
        super(new BorderLayout(10, 0));
        this.messageLabel = new JLabel("Sẵn sàng.");
        this.messageLabel.setFont(Theme.FONT_BOLD);
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        add(messageLabel, BorderLayout.CENTER);
        showInfo("Hệ thống sẵn sàng phục vụ.");
    }

    public void showInfo(String message) {
        applyState(StatusType.INFO, "[THÔNG TIN]  " + message, new Color(224, 242, 254), new Color(3, 105, 161), new Color(125, 211, 252));
    }

    public void showSuccess(String message) {
        applyState(StatusType.SUCCESS, "[THÀNH CÔNG]  " + message, new Color(220, 252, 231), new Color(21, 128, 61), new Color(134, 239, 172));
    }

    public void showWarning(String message) {
        applyState(StatusType.WARNING, "[CẢNH BÁO]  " + message, new Color(254, 243, 199), new Color(180, 83, 9), new Color(252, 211, 77));
    }

    public void showError(String message) {
        applyState(StatusType.ERROR, "[LỖI]  " + message.replace("\n", " | "), new Color(254, 226, 226), new Color(185, 28, 28), new Color(252, 165, 165));
    }

    private void applyState(StatusType type, String text, Color bg, Color fg, Color borderColor) {
        setBackground(bg);
        messageLabel.setForeground(fg);
        messageLabel.setText(text);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        putClientProperty("statusType", type.name());
        revalidate();
        repaint();
    }

    public String currentText() {
        return messageLabel.getText();
    }
}
