package com.cinema.feature.search.ui;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Movie;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.feature.search.ScreeningSearchService;
import com.cinema.shared.ui.StatusBanner;
import com.cinema.shared.ui.Theme;
import com.cinema.shared.validation.BusinessRuleException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Tìm kiếm Vận hành: Tra cứu Suất chiếu & Kiểm tra Ghế trống (Screen 3 - R6).
 * Cho phép tìm theo tên phim, chi nhánh, ngày chiếu và khoảng thời gian bắt đầu,
 * hiển thị kết quả trên JTable kèm số lượng ghế còn trống.
 */
public final class ScreeningSearchPanel extends JPanel {

    private final ScreeningSearchService service;
    private final StatusBanner statusBanner = new StatusBanner();

    private final JTextField txtMovieKeyword = new JTextField("", 14);
    private final JComboBox<String> cboMovie = new JComboBox<>();
    private final JComboBox<String> cboBranch = new JComboBox<>();
    private final JTextField txtDate = new JTextField("2026-11-04", 10);
    private final JTextField txtFromTime = new JTextField("", 6);
    private final JTextField txtToTime = new JTextField("", 6);
    private final JCheckBox chkOnlyAvailable = new JCheckBox("Chỉ suất đang mở & còn ghế trống", true);

    private final JLabel lblKpiCount = new JLabel("0 suất chiếu");
    private final JLabel lblKpiAvailableSeats = new JLabel("0 ghế trống");
    private final JLabel lblKpiSoldSeats = new JLabel("0 ghế đã bán");

    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<CinemaBranch> branches = new ArrayList<>();
    private List<Movie> movies = new ArrayList<>();

    public ScreeningSearchPanel(ScreeningSearchService service) {
        super(new BorderLayout(12, 12));
        this.service = service;
        setBackground(Theme.BG_APP);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Header
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);
        JLabel title = new JLabel("TRA CỨU LỊCH CHIẾU & KIỂM TRA GHẾ TRỐNG (SCREENING SEARCH)");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(statusBanner, BorderLayout.SOUTH);

        JPanel filterCard = buildFilterCard();

        JPanel kpiRow = new JPanel(new GridLayout(1, 3, 12, 0));
        kpiRow.setOpaque(false);
        kpiRow.add(Theme.createKpiCard("Tổng số Suất chiếu khớp điều kiện", lblKpiCount, Theme.ACCENT_PRIMARY));
        kpiRow.add(Theme.createKpiCard("Tổng số Ghế còn trống", lblKpiAvailableSeats, Theme.ACCENT_SUCCESS));
        kpiRow.add(Theme.createKpiCard("Tổng số Ghế đã bán (VALID)", lblKpiSoldSeats, Theme.ACCENT_WARNING));

        JPanel northContainer = new JPanel(new BorderLayout(10, 10));
        northContainer.setOpaque(false);
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(filterCard, BorderLayout.CENTER);
        northContainer.add(kpiRow, BorderLayout.SOUTH);

        String[] cols = {
                "Mã SC", "Mã Phim", "Tựa đề Phim", "Độ tuổi", "Thời lượng",
                "Chi nhánh Rạp", "Phòng chiếu", "Công nghệ", "Bắt đầu", "Kết thúc",
                "Giá cơ sở", "Sức chứa", "Đã bán", "Ghế còn trống", "Trạng thái"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setName("searchResultTable");
        Theme.styleTable(table);

        JPanel tableCard = Theme.createCardPanel(new BorderLayout(8, 8));
        JLabel lblResult = new JLabel("Bảng Kết quả Tra cứu Suất chiếu và Số lượng Ghế trống:");
        lblResult.setFont(Theme.FONT_SUBTITLE);
        tableCard.add(lblResult, BorderLayout.NORTH);
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

        add(northContainer, BorderLayout.NORTH);
        add(tableCard, BorderLayout.CENTER);

        loadFilterOptions();
        executeSearch();
    }

    private JPanel buildFilterCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(8, 8));
        JLabel subTitle = new JLabel("Bộ lọc Tra cứu Tham số hóa (Parameterized Query)");
        subTitle.setFont(Theme.FONT_SUBTITLE);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addFilterCell(grid, gbc, 0, 0, "Từ khóa tên/mã phim:", txtMovieKeyword);
        addFilterCell(grid, gbc, 0, 2, "Chọn bộ phim:", cboMovie);
        addFilterCell(grid, gbc, 0, 4, "Chi nhánh rạp:", cboBranch);

        addFilterCell(grid, gbc, 1, 0, "Ngày chiếu (yyyy-MM-dd):", txtDate);

        JPanel timeRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timeRangePanel.setOpaque(false);
        timeRangePanel.add(txtFromTime);
        timeRangePanel.add(new JLabel("đến"));
        timeRangePanel.add(txtToTime);
        addFilterCell(grid, gbc, 1, 2, "Khung giờ bắt đầu (HH:mm):", timeRangePanel);

        chkOnlyAvailable.setOpaque(false);
        chkOnlyAvailable.setFont(Theme.FONT_BOLD);
        gbc.gridy = 1;
        gbc.gridx = 4;
        gbc.gridwidth = 2;
        grid.add(chkOnlyAvailable, gbc);
        gbc.gridwidth = 1;

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton btnReset = Theme.createSecondaryButton("Xóa điều kiện lọc (Xem tất cả)");
        btnReset.addActionListener(e -> resetFilters());

        JButton btnSearch = Theme.createPrimaryButton("Tìm kiếm Suất chiếu");
        btnSearch.setName("btnExecuteSearch");
        btnSearch.addActionListener(e -> executeSearch());

        actions.add(btnReset);
        actions.add(btnSearch);

        card.add(subTitle, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void addFilterCell(JPanel panel, GridBagConstraints gbc, int row, int col, String label, JComponent comp) {
        gbc.gridy = row;
        gbc.gridx = col;
        gbc.weightx = 0.0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_BOLD);
        panel.add(lbl, gbc);

        gbc.gridx = col + 1;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }

    public void loadFilterOptions() {
        branches = service.listBranches();
        cboBranch.removeAllItems();
        cboBranch.addItem("Tất cả chi nhánh");
        for (CinemaBranch b : branches) {
            cboBranch.addItem(b.branchCode() + " - " + b.branchName());
        }

        movies = service.listMovies();
        cboMovie.removeAllItems();
        cboMovie.addItem("Tất cả các bộ phim");
        for (Movie m : movies) {
            cboMovie.addItem(m.movieCode() + " - " + m.title());
        }
    }

    public void resetFilters() {
        txtMovieKeyword.setText("");
        cboMovie.setSelectedIndex(0);
        cboBranch.setSelectedIndex(0);
        txtDate.setText("");
        txtFromTime.setText("");
        txtToTime.setText("");
        chkOnlyAvailable.setSelected(true);
        executeSearch();
    }

    public void executeSearch() {
        try {
            String keyword = txtMovieKeyword.getText().trim();
            Integer movieId = null;
            int mIdx = cboMovie.getSelectedIndex();
            if (mIdx > 0 && mIdx - 1 < movies.size()) {
                movieId = movies.get(mIdx - 1).movieId();
            }

            Integer branchId = null;
            int bIdx = cboBranch.getSelectedIndex();
            if (bIdx > 0 && bIdx - 1 < branches.size()) {
                branchId = branches.get(bIdx - 1).branchId();
            }

            LocalDate date = null;
            if (!txtDate.getText().trim().isEmpty()) {
                try {
                    date = LocalDate.parse(txtDate.getText().trim());
                } catch (DateTimeParseException ex) {
                    throw new BusinessRuleException("Ngày chiếu cần nhập theo định dạng yyyy-MM-dd (ví dụ: 2026-11-04).");
                }
            }

            LocalTime fromTime = null;
            if (!txtFromTime.getText().trim().isEmpty()) {
                try {
                    fromTime = LocalTime.parse(txtFromTime.getText().trim());
                } catch (DateTimeParseException ex) {
                    throw new BusinessRuleException("Giờ bắt đầu lọc cần nhập theo định dạng HH:mm (ví dụ: 08:30).");
                }
            }

            LocalTime toTime = null;
            if (!txtToTime.getText().trim().isEmpty()) {
                try {
                    toTime = LocalTime.parse(txtToTime.getText().trim());
                } catch (DateTimeParseException ex) {
                    throw new BusinessRuleException("Giờ kết thúc lọc cần nhập theo định dạng HH:mm (ví dụ: 22:30).");
                }
            }

            List<ScreeningDetailView> results = service.search(
                    keyword, movieId, branchId, date, fromTime, toTime, chkOnlyAvailable.isSelected());

            tableModel.setRowCount(0);
            int sumAvailable = 0;
            int sumSold = 0;
            for (ScreeningDetailView r : results) {
                sumAvailable += r.availableSeats();
                sumSold += r.soldSeats();
                tableModel.addRow(new Object[]{
                        "#" + r.screeningId(),
                        r.movieCode(),
                        r.movieTitle(),
                        r.ageRating(),
                        r.durationMinutes() + " phút",
                        r.branchName(),
                        r.roomName(),
                        r.roomType(),
                        Theme.formatDateTime(r.startTime()),
                        Theme.formatDateTime(r.endTime()),
                        Theme.formatCurrency(r.basePrice()),
                        r.totalSeats(),
                        r.soldSeats(),
                        r.availableSeats() + " ghế trống",
                        r.status()
                });
            }

            lblKpiCount.setText(results.size() + " suất chiếu");
            lblKpiAvailableSeats.setText(sumAvailable + " ghế trống");
            lblKpiSoldSeats.setText(sumSold + " ghế đã bán");

            if (results.isEmpty()) {
                statusBanner.showWarning("Không tìm thấy suất chiếu nào khớp với tiêu chí tra cứu.");
            } else {
                statusBanner.showSuccess("Tìm thấy " + results.size() + " suất chiếu phù hợp (Tổng cộng " + sumAvailable + " ghế còn trống).");
            }
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi tra cứu suất chiếu: " + ex.getMessage());
        }
    }
}
