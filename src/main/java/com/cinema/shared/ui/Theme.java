package com.cinema.shared.ui;

import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Hệ thống thiết kế giao diện (Design System) lấy cảm hứng từ Apple macOS Light
 * trên nền tảng FlatLaf.
 */
public final class Theme {

    public static final Color BG_APP = new Color(245, 247, 250);
    public static final Color BG_CARD = Color.WHITE;
    public static final Color BG_SIDEBAR = new Color(15, 23, 42);
    public static final Color BG_SIDEBAR_ACTIVE = new Color(14, 116, 144);
    public static final Color BG_SIDEBAR_HOVER = new Color(30, 41, 59);

    public static final Color ACCENT_PRIMARY = new Color(2, 132, 199);   // Apple/Teal Blue
    public static final Color ACCENT_TEAL = new Color(13, 148, 136);
    public static final Color ACCENT_SUCCESS = new Color(22, 163, 74);
    public static final Color ACCENT_WARNING = new Color(217, 119, 6);
    public static final Color ACCENT_DANGER = new Color(220, 38, 38);

    public static final Color TEXT_PRIMARY = new Color(15, 23, 42);
    public static final Color TEXT_MUTED = new Color(100, 116, 139);
    public static final Color BORDER_SUBTLE = new Color(226, 232, 240);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_KPI_VALUE = new Font("Segoe UI", Font.BOLD, 22);

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter ISO_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter ISO_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DecimalFormat CURRENCY_FMT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        CURRENCY_FMT = new DecimalFormat("#,##0 'VNĐ'", symbols);
    }

    private Theme() {
    }

    public static void setupLookAndFeel() {
        try {
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("Table.rowHeight", 30);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("defaultFont", FONT_BODY);
            FlatMacLightLaf.setup();
        } catch (Exception ignored) {
            // Fallback to default LAF if needed
        }
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0 VNĐ";
        }
        return CURRENCY_FMT.format(amount);
    }

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "";
    }

    public static String formatDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME_FMT) : "";
    }

    public static JPanel createCardPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BG_CARD);
        panel.setBorder(createCardBorder());
        return panel;
    }

    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        );
    }

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(ACCENT_PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return btn;
    }

    public static JButton createSuccessButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(ACCENT_TEAL);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return btn;
    }

    public static JButton createDangerButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(ACCENT_DANGER);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY);
        btn.setBackground(new Color(241, 245, 249));
        btn.setForeground(TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)
        ));
        return btn;
    }

    public static JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 6));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                        BorderFactory.createEmptyBorder(10, 14, 10, 14)
                )
        ));
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(FONT_SMALL);
        lblTitle.setForeground(TEXT_MUTED);
        valueLabel.setFont(FONT_KPI_VALUE);
        valueLabel.setForeground(TEXT_PRIMARY);
        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(30);
        table.setSelectionBackground(new Color(224, 242, 254));
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setGridColor(new Color(241, 245, 249));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(TEXT_PRIMARY);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 34));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        table.setDefaultRenderer(Object.class, renderer);
    }
}
