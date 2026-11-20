package com.cinema.feature.booking.ui;

import com.cinema.domain.BookingSummaryView;
import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Customer;
import com.cinema.domain.Employee;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.SeatAvailabilityView;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.payment.SandboxPaymentService;
import com.cinema.shared.ui.SeatMapPanel;
import com.cinema.shared.ui.StatusBanner;
import com.cinema.shared.ui.Theme;
import com.cinema.shared.ui.UiTask;
import com.cinema.shared.validation.BusinessRuleException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Màn hình Nhập liệu 2: Đặt vé & Chọn ghế theo Sơ đồ Phòng chiếu (Screen 2 - R5).
 * Hỗ trợ khách thành viên và khách vãng lai, hiển thị sơ đồ ghế trực quan,
 * tạo đơn đặt vé + vé + thanh toán trong một giao dịch nguyên tử và cho phép hủy đơn để bán lại ghế.
 */
public final class TicketBookingPanel extends JPanel {

    private final BookingService service;
    private final SandboxPaymentService sandboxPayments;
    private final StatusBanner statusBanner = new StatusBanner();
    private final SeatMapPanel seatMapPanel = new SeatMapPanel();

    private final JComboBox<String> cboQuickBranch = new JComboBox<>();
    private final JTextField txtQuickDate = new JTextField("", 9);
    private final JLabel lblOpenScreeningCount = new JLabel("Đang mở bán: 0 suất");
    private final JComboBox<ScreeningDetailView> cboScreening = new JComboBox<>();
    private final JCheckBox chkWalkInCustomer = new JCheckBox("Khách vãng lai (Mua trực tiếp tại quầy)", false);
    private final JComboBox<Customer> cboCustomer = new JComboBox<>();
    private final JComboBox<Employee> cboEmployee = new JComboBox<>();
    private final JComboBox<String> cboPaymentMethod = new JComboBox<>(new String[]{"CASH", "CARD", "TRANSFER", "EWALLET"});
    private final JComboBox<String> cboPaymentStatus = new JComboBox<>(new String[]{"SUCCESS", "PENDING"});

    private final JLabel lblSelectedSeats = new JLabel("Chưa chọn ghế nào");
    private final JLabel lblTotalPrice = new JLabel("0 VNĐ");
    private final JLabel lblHistoryTitle = new JLabel(
            "Danh sách Đơn đặt vé của Suất chiếu đang chọn (Hỗ trợ Hủy đơn & Mở bán lại ghế):");

    private final DefaultTableModel bookingTableModel;
    private final JTable bookingTable;
    private List<CinemaBranch> branches = new ArrayList<>();
    private List<BookingSummaryView> recentBookings = new ArrayList<>();
    private boolean updatingScreenings;

    public TicketBookingPanel(BookingService service) {
        this(service, null);
    }

    public TicketBookingPanel(BookingService service, SandboxPaymentService sandboxPayments) {
        super(new BorderLayout(12, 12));
        this.service = service;
        this.sandboxPayments = sandboxPayments;
        setBackground(Theme.BG_APP);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Header
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);
        JLabel title = new JLabel("ĐẶT VÉ XEM PHIM & CHỌN GHẾ TRỰC QUAN (TICKET BOOKING)");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(statusBanner, BorderLayout.SOUTH);

        seatMapPanel.setSelectionListener(this::onSeatSelectionUpdated);

        // Center: Seat Map (Left) + Booking Form (Right)
        JPanel seatCard = Theme.createCardPanel(new BorderLayout(8, 8));
        JLabel lblSeatMapTitle = new JLabel("Sơ đồ Ghế Phòng chiếu (Nhấn vào ghế trống để chọn / bỏ chọn):");
        lblSeatMapTitle.setFont(Theme.FONT_SUBTITLE);
        JScrollPane seatScroll = new JScrollPane(seatMapPanel);
        seatScroll.setBorder(null);
        seatScroll.setOpaque(false);
        seatScroll.getViewport().setBackground(Theme.BG_CARD);
        seatScroll.getVerticalScrollBar().setUnitIncrement(16);
        seatScroll.getHorizontalScrollBar().setUnitIncrement(16);
        seatCard.add(lblSeatMapTitle, BorderLayout.NORTH);
        seatCard.add(seatScroll, BorderLayout.CENTER);
        seatCard.setMinimumSize(new Dimension(260, 160));

        JPanel formCard = buildBookingFormCard();
        formCard.setMinimumSize(new Dimension(280, 160));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, seatCard, formCard);
        centerSplit.setResizeWeight(0.58);
        centerSplit.setContinuousLayout(true);
        centerSplit.setOneTouchExpandable(true);
        centerSplit.setMinimumSize(new Dimension(0, 180));
        centerSplit.setBorder(null);
        centerSplit.setOpaque(false);

        // Bottom: Recent Bookings Table
        String[] cols = {
                "Mã Đơn", "Suất chiếu", "Phim", "Phòng (Chi nhánh)", "Khách hàng",
                "Ghế đã đặt", "Số vé", "Tổng tiền", "PT Thanh toán", "TT Thanh toán", "TT Đơn"
        };
        bookingTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        bookingTable = new JTable(bookingTableModel);
        bookingTable.setName("recentBookingsTable");
        Theme.styleTable(bookingTable);

        JPanel historyCard = buildHistoryCard();
        historyCard.setMinimumSize(new Dimension(0, 140));
        historyCard.setPreferredSize(new Dimension(0, 220));

        JSplitPane mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerSplit, historyCard);
        mainVerticalSplit.setResizeWeight(0.62);
        mainVerticalSplit.setContinuousLayout(true);
        mainVerticalSplit.setOneTouchExpandable(true);
        mainVerticalSplit.setBorder(null);
        mainVerticalSplit.setOpaque(false);

        mainVerticalSplit.addComponentListener(new ComponentAdapter() {
            private boolean initialized;

            @Override
            public void componentResized(ComponentEvent e) {
                if (!initialized && mainVerticalSplit.getHeight() > 0) {
                    initialized = true;
                    int h = mainVerticalSplit.getHeight();
                    int historyHeight = Math.max(190, (int) (h * 0.34));
                    int divider = Math.max(180, h - historyHeight);
                    mainVerticalSplit.setDividerLocation(divider);
                    if (centerSplit.getWidth() > 0) {
                        centerSplit.setDividerLocation((int) (centerSplit.getWidth() * 0.56));
                    }
                }
            }
        });

        add(topPanel, BorderLayout.NORTH);
        add(mainVerticalSplit, BorderLayout.CENTER);

        loadInitialData();
    }

    private JPanel buildBookingFormCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(10, 10));
        JLabel subTitle = new JLabel("Thông tin Giao dịch Đặt vé & Thanh toán");
        subTitle.setFont(Theme.FONT_SUBTITLE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        cboScreening.setMinimumSize(new Dimension(160, 30));
        cboScreening.setPreferredSize(new Dimension(240, 30));
        cboCustomer.setMinimumSize(new Dimension(160, 30));
        cboCustomer.setPreferredSize(new Dimension(240, 30));
        cboEmployee.setMinimumSize(new Dimension(160, 30));
        cboEmployee.setPreferredSize(new Dimension(240, 30));
        cboScreening.addActionListener(e -> {
            if (!updatingScreenings) refreshSeatMapAndBookings();
        });
        chkWalkInCustomer.setOpaque(false);
        chkWalkInCustomer.setFont(Theme.FONT_BOLD);
        chkWalkInCustomer.addActionListener(e -> cboCustomer.setEnabled(!chkWalkInCustomer.isSelected()));

        int row = 0;
        JPanel quickFilterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        quickFilterRow.setOpaque(false);
        quickFilterRow.add(cboQuickBranch);
        quickFilterRow.add(new JLabel("Ngày:"));
        quickFilterRow.add(txtQuickDate);
        JButton btnQuickFilter = Theme.createSecondaryButton("Lọc suất");
        btnQuickFilter.addActionListener(e -> reloadScreeningsDropdown());
        JButton btnQuickAll = Theme.createSecondaryButton("Tất cả");
        btnQuickAll.addActionListener(e -> {
            cboQuickBranch.setSelectedIndex(0);
            txtQuickDate.setText("");
            reloadScreeningsDropdown();
        });
        quickFilterRow.add(btnQuickFilter);
        quickFilterRow.add(btnQuickAll);
        addVerticalField(form, gbc, row++, "Bộ lọc nhanh danh sách suất chiếu mở bán (Chi nhánh / yyyy-MM-dd):", quickFilterRow);

        JPanel screeningSelectHeader = new JPanel(new BorderLayout(4, 2));
        screeningSelectHeader.setOpaque(false);
        JLabel lblScr = new JLabel("1. Chọn Suất chiếu đang mở bán (*):");
        lblScr.setFont(Theme.FONT_BOLD);
        lblOpenScreeningCount.setFont(Theme.FONT_SMALL);
        lblOpenScreeningCount.setForeground(Theme.ACCENT_PRIMARY);
        screeningSelectHeader.add(lblScr, BorderLayout.WEST);
        screeningSelectHeader.add(lblOpenScreeningCount, BorderLayout.EAST);
        JPanel screeningWrapper = new JPanel(new BorderLayout(2, 2));
        screeningWrapper.setOpaque(false);
        screeningWrapper.add(screeningSelectHeader, BorderLayout.NORTH);
        screeningWrapper.add(cboScreening, BorderLayout.CENTER);
        gbc.gridy = row++;
        form.add(screeningWrapper, gbc);

        gbc.gridy = row++;
        form.add(chkWalkInCustomer, gbc);

        addVerticalField(form, gbc, row++, "2. Khách hàng thành viên:", cboCustomer);
        addVerticalField(form, gbc, row++, "3. Nhân viên thu ngân (*):", cboEmployee);
        addVerticalField(form, gbc, row++, "4. Phương thức thanh toán (*):", cboPaymentMethod);
        addVerticalField(form, gbc, row++, "5. Trạng thái thanh toán (*):", cboPaymentStatus);

        // Selected seats & total box
        JPanel summaryBox = new JPanel(new GridLayout(2, 1, 4, 4));
        summaryBox.setBackground(new Color(240, 249, 255));
        summaryBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(186, 230, 253), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        lblSelectedSeats.setFont(Theme.FONT_BOLD);
        lblTotalPrice.setFont(Theme.FONT_KPI_VALUE);
        lblTotalPrice.setForeground(Theme.ACCENT_PRIMARY);
        summaryBox.add(lblSelectedSeats);
        summaryBox.add(lblTotalPrice);

        gbc.gridy = row++;
        form.add(summaryBox, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnPanel.setOpaque(false);

        JButton btnClearSeats = Theme.createSecondaryButton("Bỏ chọn ghế");
        btnClearSeats.addActionListener(e -> seatMapPanel.clearSelection());

        JButton btnBook = Theme.createPrimaryButton("Xác nhận Đặt vé & Xuất vé");
        btnBook.setName("btnSubmitBooking");
        btnBook.addActionListener(e -> handleCreateBooking());

        btnPanel.add(btnClearSeats);
        JPanel buttonsContainer = new JPanel(new BorderLayout(0, 4));
        buttonsContainer.setOpaque(false);
        buttonsContainer.add(btnPanel, BorderLayout.NORTH);
        if (sandboxPayments != null) {
            JButton btnSandbox = Theme.createSecondaryButton("Thanh toán VNPay Sandbox");
            btnSandbox.setName("btnVnpaySandbox");
            btnSandbox.addActionListener(e -> handleSandboxPayment(btnSandbox));
            buttonsContainer.add(btnSandbox, BorderLayout.SOUTH);
        }
        btnPanel.add(btnBook);

        JPanel formHolder = new FitWidthPanel(new BorderLayout());
        formHolder.setOpaque(false);
        formHolder.add(form, BorderLayout.NORTH);
        JScrollPane formScroll = new JScrollPane(
                formHolder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        formScroll.setBorder(null);
        formScroll.setOpaque(false);
        formScroll.getViewport().setBackground(Theme.BG_CARD);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        card.add(subTitle, BorderLayout.NORTH);
        card.add(formScroll, BorderLayout.CENTER);
        card.add(buttonsContainer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(8, 8));
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        lblHistoryTitle.setFont(Theme.FONT_SUBTITLE);
        lblHistoryTitle.setMinimumSize(new Dimension(0, 24));
        header.add(lblHistoryTitle, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton btnReload = Theme.createSecondaryButton("Làm mới Sơ đồ ghế");
        btnReload.addActionListener(e -> refreshSeatMapAndBookings());

        JButton btnCancelBooking = Theme.createDangerButton("Hủy Đơn đang chọn & Giải phóng ghế");
        btnCancelBooking.setName("btnCancelBooking");
        btnCancelBooking.addActionListener(e -> handleCancelSelectedBooking());

        actions.add(btnReload);
        actions.add(btnCancelBooking);
        header.add(actions, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);
        card.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        return card;
    }

    private void addVerticalField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent comp) {
        JPanel wrapper = new JPanel(new BorderLayout(2, 2));
        wrapper.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_BOLD);
        wrapper.add(lbl, BorderLayout.NORTH);
        wrapper.add(comp, BorderLayout.CENTER);
        gbc.gridy = row;
        panel.add(wrapper, gbc);
    }

    public void loadInitialData() {
        try {
            branches = UiTask.call(this, "Đang tải danh sách chi nhánh...", service::listBranches);
            cboQuickBranch.removeAllItems();
            cboQuickBranch.addItem("Tất cả CN");
            for (CinemaBranch b : branches) {
                cboQuickBranch.addItem(b.branchCode());
            }

            cboCustomer.removeAllItems();
            for (Customer c : UiTask.call(this, "Đang tải danh sách khách hàng...", service::listCustomers)) {
                cboCustomer.addItem(c);
            }

            cboEmployee.removeAllItems();
            for (Employee emp : UiTask.call(this, "Đang tải danh sách nhân viên...", service::listEmployees)) {
                cboEmployee.addItem(emp);
            }

            reloadScreeningsDropdown();
        } catch (Exception ex) {
            statusBanner.showError("Lỗi tải dữ liệu khởi tạo màn hình Đặt vé: " + ex.getMessage());
        }
    }

    public void reloadScreeningsDropdown() {
        Integer filterBranchId = null;
        int bIdx = cboQuickBranch.getSelectedIndex();
        if (bIdx > 0 && bIdx - 1 < branches.size()) {
            filterBranchId = branches.get(bIdx - 1).branchId();
        }
        LocalDate filterDate = null;
        String dateText = txtQuickDate.getText().trim();
        if (!dateText.isEmpty()) {
            try {
                filterDate = LocalDate.parse(dateText);
            } catch (DateTimeParseException ex) {
                statusBanner.showError("Ngày lọc nhanh suất chiếu không đúng định dạng yyyy-MM-dd (ví dụ: 2026-11-04).");
                return;
            }
        }

        Integer branchId = filterBranchId;
        LocalDate date = filterDate;
        try {
            List<ScreeningDetailView> screenings = UiTask.call(this, "Đang tải các suất chiếu mở bán...",
                    () -> service.listOpenScreenings(branchId, null, date));
            ScreeningDetailView prev = (ScreeningDetailView) cboScreening.getSelectedItem();
            Integer prevId = prev != null ? prev.screeningId() : null;
            updatingScreenings = true;
            try {
                cboScreening.removeAllItems();
                lblOpenScreeningCount.setText("Hiển thị đầy đủ: " + screenings.size() + " suất mở bán");
                int toSelect = 0;
                for (int i = 0; i < screenings.size(); i++) {
                    ScreeningDetailView s = screenings.get(i);
                    cboScreening.addItem(s);
                    if (prevId != null && s.screeningId() == prevId) {
                        toSelect = i;
                    }
                }
                if (!screenings.isEmpty()) {
                    cboScreening.setSelectedIndex(toSelect);
                }
            } finally {
                updatingScreenings = false;
            }
            refreshSeatMapAndBookings();
        } catch (RuntimeException ex) {
            statusBanner.showError("Không thể tải danh sách suất chiếu: " + ex.getMessage());
        }
    }

    public void refreshSeatMapAndBookings() {
        ScreeningDetailView selected = (ScreeningDetailView) cboScreening.getSelectedItem();
        if (selected == null) {
            seatMapPanel.setSeats(List.of());
            bookingTableModel.setRowCount(0);
            lblHistoryTitle.setText("Danh sách Đơn đặt vé của Suất chiếu đang chọn (0 đơn):");
            return;
        }
        try {
            List<SeatAvailabilityView> seats = UiTask.call(this, "Đang tải sơ đồ ghế...",
                    () -> service.getSeatMap(selected.screeningId()));
            List<BookingSummaryView> bookings = UiTask.call(this, "Đang tải danh sách đơn đặt vé...",
                    () -> service.listBookingsForScreening(selected.screeningId()));
            seatMapPanel.setSeats(seats);

            recentBookings = bookings;
            lblHistoryTitle.setText(String.format(
                    "Danh sách Đơn đặt vé của Suất #%d (Hiển thị đầy đủ %d đơn — Hỗ trợ Hủy đơn & Mở bán lại ghế):",
                    selected.screeningId(), recentBookings.size()));
            bookingTableModel.setRowCount(0);
            for (BookingSummaryView b : recentBookings) {
                bookingTableModel.addRow(new Object[]{
                        b.bookingCode() + " (#" + b.bookingId() + ")",
                        "#" + b.screeningId() + " (" + Theme.formatDateTime(b.startTime()) + ")",
                        b.movieTitle(),
                        b.roomName() + " - " + b.branchName(),
                        b.customerName(),
                        b.seatLabels(),
                        b.ticketCount(),
                        Theme.formatCurrency(b.totalAmount()),
                        b.paymentMethod(),
                        b.paymentStatus(),
                        b.bookingStatus()
                });
            }
        } catch (RuntimeException ex) {
            statusBanner.showError("Không thể tải sơ đồ ghế và đơn đặt vé: " + ex.getMessage());
        }
    }

    private void onSeatSelectionUpdated(List<SeatAvailabilityView> selectedSeats) {
        if (selectedSeats.isEmpty()) {
            lblSelectedSeats.setText("Ghế đang chọn: Chưa chọn ghế nào");
            lblTotalPrice.setText("Tổng thanh toán: 0 VNĐ");
            return;
        }
        String labels = selectedSeats.stream()
                .map(s -> s.seatLabel() + "(" + s.seatTypeCode() + ")")
                .collect(Collectors.joining(", "));
        BigDecimal sum = selectedSeats.stream()
                .map(SeatAvailabilityView::finalUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSelectedSeats.setText("Ghế đang chọn (" + selectedSeats.size() + " ghế): " + labels);
        lblTotalPrice.setText("Tổng thanh toán: " + Theme.formatCurrency(sum));
    }

    public void selectFirstAvailableSeatsForDemo(int count) {
        ScreeningDetailView selected = (ScreeningDetailView) cboScreening.getSelectedItem();
        if (selected == null) {
            return;
        }
        seatMapPanel.clearSelection();
        List<SeatAvailabilityView> seats = UiTask.call(this, "Đang kiểm tra ghế trống...",
                () -> service.getSeatMap(selected.screeningId()));
        int chosen = 0;
        for (SeatAvailabilityView s : seats) {
            if (!s.occupied()) {
                seatMapPanel.toggleSeatSelection(s.seatId());
                chosen++;
                if (chosen >= count) {
                    break;
                }
            }
        }
    }

    public boolean selectAvailableSeatsByTypeCodes(String... typeCodes) {
        ScreeningDetailView selected = (ScreeningDetailView) cboScreening.getSelectedItem();
        if (selected == null || typeCodes == null || typeCodes.length == 0) {
            return false;
        }
        seatMapPanel.clearSelection();
        List<SeatAvailabilityView> seats = UiTask.call(this, "Đang kiểm tra ghế trống...",
                () -> service.getSeatMap(selected.screeningId()));
        List<Integer> chosenIds = new ArrayList<>();
        for (String code : typeCodes) {
            boolean matched = false;
            for (SeatAvailabilityView s : seats) {
                if (!s.occupied() && code.equalsIgnoreCase(s.seatTypeCode()) && !chosenIds.contains(s.seatId())) {
                    seatMapPanel.toggleSeatSelection(s.seatId());
                    chosenIds.add(s.seatId());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return chosenIds.size() == typeCodes.length;
    }

    public void setPaymentOptionsForVerification(boolean walkIn, String method, String status) {
        chkWalkInCustomer.setSelected(walkIn);
        cboCustomer.setEnabled(!walkIn);
        if (method != null) {
            cboPaymentMethod.setSelectedItem(method);
        }
        if (status != null) {
            cboPaymentStatus.setSelectedItem(status);
        }
    }

    public boolean selectBookingInTableById(int bookingId) {
        for (int i = 0; i < recentBookings.size(); i++) {
            if (recentBookings.get(i).bookingId() == bookingId) {
                bookingTable.setRowSelectionInterval(i, i);
                return true;
            }
        }
        return false;
    }

    public void handleCreateBooking() {
        try {
            ScreeningDetailView screening = (ScreeningDetailView) cboScreening.getSelectedItem();
            if (screening == null) {
                throw new BusinessRuleException("Vui lòng chọn một suất chiếu đang mở bán.");
            }
            Integer customerId = null;
            if (!chkWalkInCustomer.isSelected()) {
                Customer customer = (Customer) cboCustomer.getSelectedItem();
                if (customer == null) {
                    throw new BusinessRuleException("Vui lòng chọn khách hàng thành viên hoặc đánh dấu 'Khách vãng lai'.");
                }
                customerId = customer.customerId();
            }
            Employee employee = (Employee) cboEmployee.getSelectedItem();
            if (employee == null) {
                throw new BusinessRuleException("Vui lòng chọn nhân viên thu ngân.");
            }

            List<SeatAvailabilityView> selectedSeats = seatMapPanel.getSelectedSeats();
            List<Integer> seatIds = selectedSeats.stream().map(SeatAvailabilityView::seatId).toList();
            String method = (String) cboPaymentMethod.getSelectedItem();
            String paymentStatus = (String) cboPaymentStatus.getSelectedItem();

            Integer selectedCustomerId = customerId;
            int bookingId = UiTask.call(this, "Đang đặt vé và ghi nhận thanh toán...", () -> service.createBooking(
                    screening.screeningId(),
                    selectedCustomerId,
                    employee.employeeId(),
                    seatIds,
                    method,
                    paymentStatus
            ));

            String seatLabels = selectedSeats.stream().map(SeatAvailabilityView::seatLabel).collect(Collectors.joining(", "));
            reloadScreeningsDropdown();
            statusBanner.showSuccess(String.format(
                    "Đặt vé thành công! Mã đơn #%d | Suất #%d (%s) | Ghế: %s | Thanh toán: %s (%s).",
                    bookingId, screening.screeningId(), screening.movieTitle(), seatLabels, method, paymentStatus));
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi thực hiện giao dịch đặt vé: " + ex.getMessage());
        }
    }

    private void handleSandboxPayment(JButton button) {
        ScreeningDetailView selected = (ScreeningDetailView) cboScreening.getSelectedItem();
        Employee employee = (Employee) cboEmployee.getSelectedItem();
        Customer customer = (Customer) cboCustomer.getSelectedItem();
        if (selected == null || employee == null || (!chkWalkInCustomer.isSelected() && customer == null)) {
            statusBanner.showError("Vui lòng chọn suất chiếu, nhân viên và khách hàng hoặc khách vãng lai.");
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            statusBanner.showError("Máy chưa hỗ trợ mở trình duyệt để thanh toán sandbox.");
            return;
        }
        Integer customerId = chkWalkInCustomer.isSelected() ? null : customer.customerId();
        List<Integer> seatIds = seatMapPanel.getSelectedSeats().stream().map(SeatAvailabilityView::seatId).toList();
        button.setEnabled(false);
        new SwingWorker<SandboxPaymentService.Checkout,Void>() {
            @Override protected SandboxPaymentService.Checkout doInBackground() {
                return sandboxPayments.create(selected.screeningId(),customerId,employee.employeeId(),seatIds);
            }
            @Override protected void done() {
                button.setEnabled(true);
                try {
                    var checkout=get();
                    Desktop.getDesktop().browse(checkout.url());
                    reloadScreeningsDropdown();
                    statusBanner.showWarning("Đơn #"+checkout.bookingId()+" giữ ghế 15 phút. Chỉ IPN xác minh mới xác nhận vé; làm mới để xem kết quả. Đây là sandbox.");
                } catch(Exception ex) {
                    Throwable cause=ex.getCause()!=null?ex.getCause():ex;
                    statusBanner.showError("Chưa mở được thanh toán sandbox: "+cause.getMessage());
                }
            }
        }.execute();
    }

    public void handleCancelSelectedBooking() {
        int row = bookingTable.getSelectedRow();
        if (row < 0 || row >= recentBookings.size()) {
            statusBanner.showWarning("Vui lòng chọn một đơn đặt vé trong bảng bên dưới để hủy.");
            return;
        }
        BookingSummaryView item = recentBookings.get(row);
        try {
            UiTask.run(this, "Đang hủy đơn và giải phóng ghế...", () -> service.cancelBooking(item.bookingId()));
            reloadScreeningsDropdown();
            statusBanner.showSuccess(String.format(
                    "Đã hủy thành công đơn %s (#%d). Các ghế [%s] đã được giải phóng để bán lại.",
                    item.bookingCode(), item.bookingId(), item.seatLabels()));
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi hủy đơn đặt vé: " + ex.getMessage());
        }
    }

    public StatusBanner getStatusBanner() {
        return statusBanner;
    }

    public SeatMapPanel getSeatMapPanel() {
        return seatMapPanel;
    }

    public List<BookingSummaryView> getRecentBookings() {
        return List.copyOf(recentBookings);
    }

    public boolean selectScreeningById(int screeningId) {
        reloadScreeningsDropdown();
        for (int i = 0; i < cboScreening.getItemCount(); i++) {
            ScreeningDetailView item = cboScreening.getItemAt(i);
            if (item != null && item.screeningId() == screeningId) {
                cboScreening.setSelectedIndex(i);
                refreshSeatMapAndBookings();
                return true;
            }
        }
        if (cboQuickBranch.getSelectedIndex() > 0 || !txtQuickDate.getText().trim().isEmpty()) {
            cboQuickBranch.setSelectedIndex(0);
            txtQuickDate.setText("");
            reloadScreeningsDropdown();
            for (int i = 0; i < cboScreening.getItemCount(); i++) {
                ScreeningDetailView item = cboScreening.getItemAt(i);
                if (item != null && item.screeningId() == screeningId) {
                    cboScreening.setSelectedIndex(i);
                    refreshSeatMapAndBookings();
                    return true;
                }
            }
        }
        return false;
    }

    private static final class FitWidthPanel extends JPanel implements Scrollable {
        FitWidthPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
