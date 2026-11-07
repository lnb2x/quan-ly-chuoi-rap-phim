package com.cinema.shared.ui;

import com.cinema.domain.SeatAvailabilityView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sơ đồ ghế trực quan trong phòng chiếu (8 hàng A-H x 10 ghế = 80 ghế).
 * Cho phép chọn/bỏ chọn các ghế còn trống và chặn chọn ghế đã bán (OCCUPIED).
 */
public final class SeatMapPanel extends JPanel {

    private static final Color COLOR_STD = new Color(224, 242, 254);
    private static final Color COLOR_VIP = new Color(254, 243, 199);
    private static final Color COLOR_COUPLE = new Color(243, 232, 255);
    private static final Color COLOR_SELECTED = new Color(16, 185, 129);
    private static final Color COLOR_OCCUPIED = new Color(254, 202, 202);

    private final JPanel gridPanel;
    private final Map<Integer, SeatAvailabilityView> seatById = new LinkedHashMap<>();
    private final Map<Integer, SeatAvailabilityView> selectedSeats = new LinkedHashMap<>();
    private final Map<Integer, JButton> buttonBySeatId = new LinkedHashMap<>();
    private Consumer<List<SeatAvailabilityView>> selectionListener;

    public SeatMapPanel() {
        super(new BorderLayout(8, 10));
        setBackground(Theme.BG_CARD);

        // Thanh màn hình chiếu ở phía trên
        JPanel screenHeader = new JPanel(new BorderLayout());
        screenHeader.setBackground(new Color(30, 41, 59));
        screenHeader.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        JLabel lblScreen = new JLabel("MÀN HÌNH CHIẾU PHIM (SCREEN)", SwingConstants.CENTER);
        lblScreen.setFont(Theme.FONT_BOLD);
        lblScreen.setForeground(Color.WHITE);
        screenHeader.add(lblScreen, BorderLayout.CENTER);

        gridPanel = new JPanel(new GridLayout(8, 10, 6, 6));
        gridPanel.setBackground(Theme.BG_CARD);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

        JPanel legendPanel = createLegendPanel();

        add(screenHeader, BorderLayout.NORTH);
        add(gridPanel, BorderLayout.CENTER);
        add(legendPanel, BorderLayout.SOUTH);
    }

    private JPanel createLegendPanel() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 4));
        legend.setBackground(Theme.BG_CARD);
        legend.add(createLegendItem("Ghế Thường (STD)", COLOR_STD, Theme.TEXT_PRIMARY));
        legend.add(createLegendItem("Ghế VIP (+20k)", COLOR_VIP, Theme.TEXT_PRIMARY));
        legend.add(createLegendItem("Ghế Đôi (+45k)", COLOR_COUPLE, Theme.TEXT_PRIMARY));
        legend.add(createLegendItem("Đang chọn", COLOR_SELECTED, Color.WHITE));
        legend.add(createLegendItem("Đã bán (Không thể chọn)", COLOR_OCCUPIED, new Color(153, 27, 27)));
        return legend;
    }

    private JPanel createLegendItem(String label, Color bg, Color fg) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setBackground(Theme.BG_CARD);
        JLabel swatch = new JLabel("   ");
        swatch.setOpaque(true);
        swatch.setBackground(bg);
        swatch.setForeground(fg);
        swatch.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SUBTLE));
        JLabel text = new JLabel(label);
        text.setFont(Theme.FONT_SMALL);
        item.add(swatch);
        item.add(text);
        return item;
    }

    public void setSelectionListener(Consumer<List<SeatAvailabilityView>> listener) {
        this.selectionListener = listener;
    }

    public void setSeats(List<SeatAvailabilityView> seats) {
        seatById.clear();
        selectedSeats.clear();
        buttonBySeatId.clear();
        gridPanel.removeAll();

        if (seats == null || seats.isEmpty()) {
            gridPanel.setLayout(new BorderLayout());
            JLabel empty = new JLabel("Vui lòng chọn suất chiếu để hiển thị sơ đồ ghế.", SwingConstants.CENTER);
            empty.setFont(Theme.FONT_BODY);
            empty.setForeground(Theme.TEXT_MUTED);
            gridPanel.add(empty, BorderLayout.CENTER);
        } else {
            gridPanel.setLayout(new GridLayout(8, 10, 6, 6));
            for (SeatAvailabilityView seat : seats) {
                seatById.put(seat.seatId(), seat);
                JButton btn = createSeatButton(seat);
                buttonBySeatId.put(seat.seatId(), btn);
                gridPanel.add(btn);
            }
        }

        revalidate();
        repaint();
        notifySelectionChanged();
    }

    private JButton createSeatButton(SeatAvailabilityView seat) {
        JButton btn = new JButton(seat.seatLabel());
        btn.setName("seat_" + seat.seatLabel());
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(4, 2, 4, 2));
        btn.setToolTipText(String.format("Ghế %s - %s - Giá: %s (%s)",
                seat.seatLabel(),
                seat.seatTypeName(),
                Theme.formatCurrency(seat.finalUnitPrice()),
                seat.occupied() ? "Đã bán" : "Còn trống"));

        if (seat.occupied()) {
            btn.setBackground(COLOR_OCCUPIED);
            btn.setForeground(new Color(153, 27, 27));
            btn.setText(seat.seatLabel());
            btn.setBorder(BorderFactory.createLineBorder(new Color(252, 165, 165), 1, true));
            btn.setEnabled(false);
        } else {
            applyUnselectedStyle(btn, seat);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> toggleSeatSelection(seat.seatId()));
        }
        return btn;
    }

    private void applyUnselectedStyle(JButton btn, SeatAvailabilityView seat) {
        switch (seat.seatTypeCode()) {
            case "VIP" -> btn.setBackground(COLOR_VIP);
            case "COUPLE" -> btn.setBackground(COLOR_COUPLE);
            default -> btn.setBackground(COLOR_STD);
        }
        btn.setForeground(Theme.TEXT_PRIMARY);
        btn.setBorder(BorderFactory.createLineBorder(new Color(148, 163, 184), 1, true));
    }

    public void toggleSeatSelection(int seatId) {
        SeatAvailabilityView seat = seatById.get(seatId);
        JButton btn = buttonBySeatId.get(seatId);
        if (seat == null || btn == null || seat.occupied()) {
            return;
        }
        if (selectedSeats.containsKey(seatId)) {
            selectedSeats.remove(seatId);
            applyUnselectedStyle(btn, seat);
        } else {
            selectedSeats.put(seatId, seat);
            btn.setBackground(COLOR_SELECTED);
            btn.setForeground(Color.WHITE);
            btn.setBorder(BorderFactory.createLineBorder(new Color(5, 150, 105), 2, true));
        }
        notifySelectionChanged();
    }

    public void clearSelection() {
        for (Integer seatId : new ArrayList<>(selectedSeats.keySet())) {
            toggleSeatSelection(seatId);
        }
    }

    public List<SeatAvailabilityView> getSelectedSeats() {
        return new ArrayList<>(selectedSeats.values());
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(getSelectedSeats());
        }
    }
}
