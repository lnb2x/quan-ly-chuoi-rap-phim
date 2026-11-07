package com.cinema.feature.screening.ui;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Movie;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.ScreeningRoom;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.shared.ui.StatusBanner;
import com.cinema.shared.ui.Theme;
import com.cinema.shared.validation.BusinessRuleException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Nhập liệu 1: Quản lý Lịch chiếu & Suất chiếu (Screen 1 - R5).
 * Cho phép tạo mới, cập nhật, hủy suất chiếu và kiểm tra hợp lệ thời lượng,
 * giá cơ sở và chống chồng lấn lịch chiếu trong cùng một phòng chiếu.
 */
public final class ScreeningManagementPanel extends JPanel {

    private final ScreeningService service;
    private final StatusBanner statusBanner = new StatusBanner();

    private final JComboBox<CinemaBranch> cboBranch = new JComboBox<>();
    private final JComboBox<ScreeningRoom> cboRoom = new JComboBox<>();
    private final JComboBox<Movie> cboMovie = new JComboBox<>();
    private final JTextField txtScreeningDate = new JTextField("2026-11-11", 10);
    private final JTextField txtStartTime = new JTextField("09:00", 6);
    private final JTextField txtEndTime = new JTextField("11:10", 6);
    private final JTextField txtBasePrice = new JTextField("95000", 10);
    private final JComboBox<String> cboStatus = new JComboBox<>(new String[]{"OPEN", "CANCELLED"});

    private final JComboBox<String> cboFilterBranch = new JComboBox<>();
    private final JTextField txtFilterDate = new JTextField("", 10);

    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<ScreeningDetailView> currentRows = new ArrayList<>();
    private List<CinemaBranch> branches = new ArrayList<>();
    private Integer selectedScreeningId = null;
    private final JLabel lblSelectedId = new JLabel("Chế độ: Tạo suất chiếu mới");

    public ScreeningManagementPanel(ScreeningService service) {
        super(new BorderLayout(14, 14));
        this.service = service;
        setBackground(Theme.BG_APP);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Header
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);
        JLabel title = new JLabel("QUẢN LÝ LỊCH CHIẾU & SUẤT CHIẾU (SCREENING MANAGEMENT)");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(statusBanner, BorderLayout.SOUTH);

        // Form Card
        JPanel formCard = buildFormCard();

        // Table Card
        String[] cols = {
                "Mã SC", "Chi nhánh", "Phòng chiếu", "Loại", "Tựa đề Phim",
                "Thời lượng", "Bắt đầu", "Kết thúc", "Giá cơ sở", "Đã bán / Sức chứa", "Trạng thái"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setName("screeningTable");
        Theme.styleTable(table);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                populateFormFromSelectedRow();
            }
        });

        JPanel tableCard = buildTableCard();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formCard, tableCard);
        splitPane.setResizeWeight(0.36);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        loadReferenceData();
        refreshTable();
    }

    private JPanel buildFormCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(12, 12));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        JLabel subTitle = new JLabel("Thông tin Suất chiếu (Kiểm tra hợp lệ Thời lượng & Chống trùng lịch phòng)");
        subTitle.setFont(Theme.FONT_SUBTITLE);
        lblSelectedId.setFont(Theme.FONT_BOLD);
        lblSelectedId.setForeground(Theme.ACCENT_PRIMARY);
        headerRow.add(subTitle, BorderLayout.WEST);
        headerRow.add(lblSelectedId, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        cboBranch.addActionListener(e -> onBranchSelectionChanged());
        cboMovie.addActionListener(e -> autoSuggestEndTime());

        // Row 0
        addFormField(grid, gbc, 0, 0, "Chi nhánh rạp (*):", cboBranch);
        addFormField(grid, gbc, 0, 2, "Phòng chiếu (*):", cboRoom);

        // Row 1
        addFormField(grid, gbc, 1, 0, "Bộ phim trình chiếu (*):", cboMovie);
        addFormField(grid, gbc, 1, 2, "Ngày chiếu (yyyy-MM-dd) (*):", txtScreeningDate);

        // Row 2
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timePanel.setOpaque(false);
        timePanel.add(txtStartTime);
        timePanel.add(new JLabel("đến"));
        timePanel.add(txtEndTime);
        JButton btnAutoEnd = Theme.createSecondaryButton("Tính giờ kết thúc (+20p dọn phòng)");
        btnAutoEnd.addActionListener(e -> autoSuggestEndTime());
        timePanel.add(btnAutoEnd);

        addFormField(grid, gbc, 2, 0, "Khung giờ (HH:mm) (*):", timePanel);
        addFormField(grid, gbc, 2, 2, "Giá cơ sở VNĐ (*):", txtBasePrice);

        // Row 3
        addFormField(grid, gbc, 3, 0, "Trạng thái suất chiếu:", cboStatus);

        // Action buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        actions.setOpaque(false);

        JButton btnCreate = Theme.createPrimaryButton("+ Thêm Suất chiếu mới");
        btnCreate.setName("btnCreateScreening");
        btnCreate.addActionListener(e -> handleCreateScreening());

        JButton btnUpdate = Theme.createSuccessButton("Lưu Cập nhật Suất đang chọn");
        btnUpdate.setName("btnUpdateScreening");
        btnUpdate.addActionListener(e -> handleUpdateScreening());

        JButton btnClear = Theme.createSecondaryButton("Làm mới biểu mẫu");
        btnClear.addActionListener(e -> clearForm());

        actions.add(btnClear);
        actions.add(btnUpdate);
        actions.add(btnCreate);

        card.add(headerRow, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildTableCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(10, 10));

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filterBar.setOpaque(false);
        JLabel lblList = new JLabel("Danh sách Suất chiếu hiện có:");
        lblList.setFont(Theme.FONT_SUBTITLE);
        filterBar.add(lblList);
        filterBar.add(Box.createHorizontalStrut(12));
        filterBar.add(new JLabel("Lọc Chi nhánh:"));
        filterBar.add(cboFilterBranch);
        filterBar.add(new JLabel("Ngày chiếu (yyyy-MM-dd):"));
        filterBar.add(txtFilterDate);

        JButton btnFilter = Theme.createSecondaryButton("Lọc danh sách");
        btnFilter.addActionListener(e -> refreshTable());
        JButton btnResetFilter = Theme.createSecondaryButton("Tất cả");
        btnResetFilter.addActionListener(e -> {
            cboFilterBranch.setSelectedIndex(0);
            txtFilterDate.setText("");
            refreshTable();
        });
        filterBar.add(btnFilter);
        filterBar.add(btnResetFilter);

        card.add(filterBar, BorderLayout.NORTH);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, int row, int col, String labelText, JComponent comp) {
        gbc.gridy = row;
        gbc.gridx = col;
        gbc.weightx = 0.0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(Theme.FONT_BOLD);
        panel.add(lbl, gbc);

        gbc.gridx = col + 1;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }

    public void loadReferenceData() {
        try {
            branches = service.listBranches();
            cboBranch.removeAllItems();
            cboFilterBranch.removeAllItems();
            cboFilterBranch.addItem("Tất cả chi nhánh");
            for (CinemaBranch b : branches) {
                cboBranch.addItem(b);
                cboFilterBranch.addItem(b.branchCode() + " - " + b.branchName());
            }

            cboMovie.removeAllItems();
            for (Movie m : service.listMovies()) {
                cboMovie.addItem(m);
            }

            onBranchSelectionChanged();
            autoSuggestEndTime();
        } catch (Exception ex) {
            statusBanner.showError("Không thể tải danh mục ban đầu: " + ex.getMessage());
        }
    }

    private void onBranchSelectionChanged() {
        CinemaBranch selectedBranch = (CinemaBranch) cboBranch.getSelectedItem();
        cboRoom.removeAllItems();
        if (selectedBranch != null) {
            for (ScreeningRoom r : service.listRoomsByBranch(selectedBranch.branchId())) {
                cboRoom.addItem(r);
            }
        }
    }

    public void autoSuggestEndTime() {
        Movie movie = (Movie) cboMovie.getSelectedItem();
        if (movie == null) {
            return;
        }
        try {
            LocalTime st = LocalTime.parse(txtStartTime.getText().trim());
            LocalTime suggestedEnd = st.plusMinutes(movie.durationMinutes() + 20L);
            txtEndTime.setText(String.format("%02d:%02d", suggestedEnd.getHour(), suggestedEnd.getMinute()));
        } catch (DateTimeParseException ignored) {
            // Keep existing end time if start time is being edited
        }
    }

    public void refreshTable() {
        try {
            Integer branchId = null;
            int idx = cboFilterBranch.getSelectedIndex();
            if (idx > 0 && idx - 1 < branches.size()) {
                branchId = branches.get(idx - 1).branchId();
            }
            LocalDate dateFilter = null;
            String dateStr = txtFilterDate.getText().trim();
            if (!dateStr.isEmpty()) {
                dateFilter = LocalDate.parse(dateStr);
            }

            currentRows = service.listScreenings(branchId, null, null, dateFilter);
            tableModel.setRowCount(0);
            for (ScreeningDetailView row : currentRows) {
                tableModel.addRow(new Object[]{
                        "#" + row.screeningId(),
                        row.branchName(),
                        row.roomName(),
                        row.roomType(),
                        row.movieTitle(),
                        row.durationMinutes() + " phút",
                        Theme.formatDateTime(row.startTime()),
                        Theme.formatDateTime(row.endTime()),
                        Theme.formatCurrency(row.basePrice()),
                        row.soldSeats() + " / " + row.totalSeats(),
                        "OPEN".equals(row.status()) ? "Đang mở bán (OPEN)" : "Đã hủy (CANCELLED)"
                });
            }
            if (currentRows.isEmpty()) {
                statusBanner.showWarning("Không tìm thấy suất chiếu nào phù hợp với bộ lọc.");
            }
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi lọc danh sách suất chiếu: " + ex.getMessage());
        }
    }

    private void populateFormFromSelectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0 || viewRow >= currentRows.size()) {
            return;
        }
        ScreeningDetailView item = currentRows.get(viewRow);
        selectedScreeningId = item.screeningId();
        lblSelectedId.setText("Đang chọn Suất chiếu #" + item.screeningId());

        for (int i = 0; i < cboBranch.getItemCount(); i++) {
            if (cboBranch.getItemAt(i).branchId() == item.branchId()) {
                cboBranch.setSelectedIndex(i);
                break;
            }
        }
        onBranchSelectionChanged();
        for (int i = 0; i < cboRoom.getItemCount(); i++) {
            if (cboRoom.getItemAt(i).roomId() == item.roomId()) {
                cboRoom.setSelectedIndex(i);
                break;
            }
        }
        for (int i = 0; i < cboMovie.getItemCount(); i++) {
            if (cboMovie.getItemAt(i).movieId() == item.movieId()) {
                cboMovie.setSelectedIndex(i);
                break;
            }
        }
        if (item.startTime() != null) {
            txtScreeningDate.setText(item.startTime().toLocalDate().toString());
            txtStartTime.setText(String.format("%02d:%02d", item.startTime().getHour(), item.startTime().getMinute()));
        }
        if (item.endTime() != null) {
            txtEndTime.setText(String.format("%02d:%02d", item.endTime().getHour(), item.endTime().getMinute()));
        }
        txtBasePrice.setText(item.basePrice().stripTrailingZeros().toPlainString());
        cboStatus.setSelectedItem(item.status());
    }

    public void clearForm() {
        selectedScreeningId = null;
        lblSelectedId.setText("Chế độ: Tạo suất chiếu mới");
        table.clearSelection();
        txtScreeningDate.setText("2026-11-11");
        txtStartTime.setText("09:00");
        autoSuggestEndTime();
        txtBasePrice.setText("95000");
        cboStatus.setSelectedItem("OPEN");
        statusBanner.showInfo("Đã đặt lại biểu mẫu tạo mới suất chiếu.");
    }

    public void setFormValuesForVerification(int branchIndex, int roomIndex, int movieIndex,
                                             String dateStr, String startStr, String endStr, String priceStr) {
        if (branchIndex >= 0 && branchIndex < cboBranch.getItemCount()) {
            cboBranch.setSelectedIndex(branchIndex);
            onBranchSelectionChanged();
        }
        if (roomIndex >= 0 && roomIndex < cboRoom.getItemCount()) {
            cboRoom.setSelectedIndex(roomIndex);
        }
        if (movieIndex >= 0 && movieIndex < cboMovie.getItemCount()) {
            cboMovie.setSelectedIndex(movieIndex);
        }
        txtScreeningDate.setText(dateStr);
        txtStartTime.setText(startStr);
        txtEndTime.setText(endStr);
        txtBasePrice.setText(priceStr);
        cboStatus.setSelectedItem("OPEN");
    }

    public void handleCreateScreening() {
        try {
            Movie movie = (Movie) cboMovie.getSelectedItem();
            ScreeningRoom room = (ScreeningRoom) cboRoom.getSelectedItem();
            LocalDate date = parseDateField();
            LocalTime startClock = parseTimeField(txtStartTime.getText(), "giờ bắt đầu");
            LocalTime endClock = parseTimeField(txtEndTime.getText(), "giờ kết thúc");
            BigDecimal basePrice = parsePriceField();

            LocalDateTime startDt = LocalDateTime.of(date, startClock);
            LocalDateTime endDt = endClock.isAfter(startClock)
                    ? LocalDateTime.of(date, endClock)
                    : LocalDateTime.of(date.plusDays(1), endClock);

            int newId = service.createScreening(movie.movieId(), room.roomId(), startDt, endDt, basePrice);
            refreshTable();
            statusBanner.showSuccess(String.format(
                    "Đã tạo thành công Suất chiếu #%d cho phim '%s' tại %s (%s - %s).",
                    newId, movie.title(), room.roomName(),
                    Theme.formatDateTime(startDt), Theme.formatDateTime(endDt)));
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi thêm suất chiếu: " + ex.getMessage());
        }
    }

    public void handleUpdateScreening() {
        if (selectedScreeningId == null) {
            statusBanner.showWarning("Vui lòng chọn một suất chiếu trong bảng bên dưới trước khi nhấn Lưu Cập nhật.");
            return;
        }
        try {
            Movie movie = (Movie) cboMovie.getSelectedItem();
            ScreeningRoom room = (ScreeningRoom) cboRoom.getSelectedItem();
            LocalDate date = parseDateField();
            LocalTime startClock = parseTimeField(txtStartTime.getText(), "giờ bắt đầu");
            LocalTime endClock = parseTimeField(txtEndTime.getText(), "giờ kết thúc");
            BigDecimal basePrice = parsePriceField();
            String status = (String) cboStatus.getSelectedItem();

            LocalDateTime startDt = LocalDateTime.of(date, startClock);
            LocalDateTime endDt = endClock.isAfter(startClock)
                    ? LocalDateTime.of(date, endClock)
                    : LocalDateTime.of(date.plusDays(1), endClock);

            service.updateScreening(selectedScreeningId, movie.movieId(), room.roomId(), startDt, endDt, basePrice, status);
            refreshTable();
            statusBanner.showSuccess("Đã cập nhật thành công Suất chiếu #" + selectedScreeningId + ".");
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi cập nhật suất chiếu: " + ex.getMessage());
        }
    }

    private LocalDate parseDateField() {
        try {
            return LocalDate.parse(txtScreeningDate.getText().trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("Ngày chiếu không đúng định dạng chuẩn yyyy-MM-dd (ví dụ: 2026-11-11).");
        }
    }

    private LocalTime parseTimeField(String text, String label) {
        try {
            return LocalTime.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("Định dạng " + label + " không hợp lệ. Vui lòng nhập theo chuẩn HH:mm (ví dụ: 09:00).");
        }
    }

    private BigDecimal parsePriceField() {
        try {
            return new BigDecimal(txtBasePrice.getText().trim().replace(".", "").replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("Giá vé cơ sở phải là một số hợp lệ lớn hơn 0 (ví dụ: 95000).");
        }
    }

    public StatusBanner getStatusBanner() {
        return statusBanner;
    }
}
