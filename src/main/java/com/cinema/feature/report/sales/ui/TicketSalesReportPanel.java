package com.cinema.feature.report.sales.ui;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.report.sales.TicketSalesReportService;
import com.cinema.shared.ui.StatusBanner;
import com.cinema.shared.ui.Theme;
import com.cinema.shared.ui.UiTask;
import com.cinema.shared.validation.BusinessRuleException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Báo cáo 2: Báo cáo Tình hình Bán vé và Tỷ lệ Lấp đầy theo Suất chiếu.
 * Người lập báo cáo: Quản lý Chi nhánh.
 * Người nhận báo cáo: Ban Điều hành và Quản lý Kinh doanh.
 * Hỗ trợ xem trước trên màn hình (Preview) và xuất tệp PDF tiếng Việt.
 */
public final class TicketSalesReportPanel extends JPanel {

    private final TicketSalesReportService service;
    private final StatusBanner statusBanner = new StatusBanner();

    private final JTextField txtFromDate = new JTextField("2026-11-04", 10);
    private final JTextField txtToDate = new JTextField("2026-11-10", 10);
    private final JComboBox<String> cboBranch = new JComboBox<>();
    private final JComboBox<String> cboScreening = new JComboBox<>();

    private final JLabel lblKpiScreenings = new JLabel("0 suất");
    private final JLabel lblKpiSoldVsCap = new JLabel("0 / 0 ghế");
    private final JLabel lblKpiOccupancy = new JLabel("0.00%");
    private final JLabel lblKpiRevenue = new JLabel("0 VNĐ");

    private final DefaultTableModel tableModel;
    private List<CinemaBranch> branches = new ArrayList<>();
    private List<ScreeningDetailView> screenings = new ArrayList<>();
    private TicketSalesReportData currentReportData;

    public TicketSalesReportPanel(TicketSalesReportService service) {
        super(new BorderLayout(12, 12));
        this.service = service;
        setBackground(Theme.BG_APP);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);
        JLabel title = new JLabel("Báo cáo bán vé");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(statusBanner, BorderLayout.SOUTH);

        JPanel filterCard = buildFilterCard();

        JPanel kpiRow = new JPanel(new GridLayout(1, 4, 12, 0));
        kpiRow.setOpaque(false);
        kpiRow.add(Theme.createKpiCard("Số suất chiếu", lblKpiScreenings, Theme.ACCENT_PRIMARY));
        kpiRow.add(Theme.createKpiCard("Vé đã bán / sức chứa", lblKpiSoldVsCap, Theme.ACCENT_TEAL));
        kpiRow.add(Theme.createKpiCard("Lấp đầy trung bình", lblKpiOccupancy, Theme.ACCENT_WARNING));
        kpiRow.add(Theme.createKpiCard("Doanh thu", lblKpiRevenue, Theme.ACCENT_SUCCESS));

        JPanel northContainer = new JPanel(new BorderLayout(10, 10));
        northContainer.setOpaque(false);
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(filterCard, BorderLayout.CENTER);
        northContainer.add(kpiRow, BorderLayout.SOUTH);

        String[] cols = {
                "Mã suất", "Ngày chiếu", "Bắt đầu", "Kết thúc", "Chi nhánh", "Phòng chiếu",
                "Loại", "Phim", "Sức chứa", "Vé đã bán", "Ghế trống", "Lấp đầy", "Doanh thu"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        table.setName("ticketSalesReportTable");
        Theme.styleTable(table);

        JPanel tableCard = Theme.createCardPanel(new BorderLayout(8, 8));
        JLabel lblTable = new JLabel("Chi tiết theo suất chiếu");
        lblTable.setFont(Theme.FONT_SUBTITLE);
        tableCard.add(lblTable, BorderLayout.NORTH);
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

        add(northContainer, BorderLayout.NORTH);
        add(tableCard, BorderLayout.CENTER);

        loadFilterOptions();
        refreshPreview();
    }

    private JPanel buildFilterCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(8, 6));
        JPanel metaBanner = new JPanel(new BorderLayout());
        metaBanner.setOpaque(false);
        JLabel roleInfo = new JLabel(
                "Người lập: Nguyễn Minh Hoàng (quản lý chi nhánh)   |   Người nhận: Ban điều hành và quản lý kinh doanh");
        roleInfo.setFont(Theme.FONT_BOLD);
        roleInfo.setForeground(new Color(3, 105, 161));
        metaBanner.add(roleInfo, BorderLayout.WEST);

        JPanel dates = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dates.setOpaque(false);
        dates.add(new JLabel("Từ ngày:"));
        dates.add(txtFromDate);
        dates.add(new JLabel("Đến ngày:"));
        dates.add(txtToDate);

        JLabel branchLabel = new JLabel("Chi nhánh:");
        branchLabel.setLabelFor(cboBranch);
        cboBranch.addActionListener(e -> reloadScreeningFilter());
        JLabel screeningLabel = new JLabel("Suất chiếu:");
        screeningLabel.setLabelFor(cboScreening);
        cboScreening.setName("ticketSalesScreeningFilter");

        JPanel selectorLabels = new JPanel(new GridLayout(2, 1, 0, 6));
        selectorLabels.setOpaque(false);
        selectorLabels.add(branchLabel);
        selectorLabels.add(screeningLabel);

        JPanel selectors = new JPanel(new GridLayout(2, 1, 0, 6));
        selectors.setOpaque(false);
        selectors.add(cboBranch);
        selectors.add(cboScreening);

        JPanel controls = new JPanel(new BorderLayout(8, 6));
        controls.setOpaque(false);
        controls.add(dates, BorderLayout.NORTH);
        controls.add(selectorLabels, BorderLayout.WEST);
        controls.add(selectors, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        actions.setOpaque(false);

        JButton btnPreview = Theme.createPrimaryButton("Xem trước");
        btnPreview.setName("btnPreviewTicketSales");
        btnPreview.addActionListener(e -> refreshPreview());

        JButton btnExportPdf = Theme.createSuccessButton("Xuất PDF");
        btnExportPdf.setName("btnExportTicketSalesPdf");
        btnExportPdf.addActionListener(e -> {
            try {
                exportDefaultPdf();
            } catch (RuntimeException ex) {
                statusBanner.showError(ex.getMessage());
            }
        });

        actions.add(btnPreview);
        actions.add(btnExportPdf);

        card.add(metaBanner, BorderLayout.NORTH);
        card.add(controls, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void loadFilterOptions() {
        branches = UiTask.call(this, "Đang tải chi nhánh…", service::listBranches);
        cboBranch.removeAllItems();
        cboBranch.addItem("Tất cả chi nhánh");
        for (CinemaBranch b : branches) {
            cboBranch.addItem(b.branchCode() + " - " + b.branchName());
        }
        reloadScreeningFilter();
    }

    private void reloadScreeningFilter() {
        Integer branchId = null;
        int idx = cboBranch.getSelectedIndex();
        if (idx > 0 && idx - 1 < branches.size()) {
            branchId = branches.get(idx - 1).branchId();
        }
        Integer selectedBranchId = branchId;
        screenings = UiTask.call(this, "Đang tải suất chiếu…", () -> service.listScreeningsForBranch(selectedBranchId));
        cboScreening.removeAllItems();
        cboScreening.addItem("Tất cả suất chiếu");
        for (ScreeningDetailView s : screenings) {
            cboScreening.addItem("#" + s.screeningId() + " - " + s.movieTitle() + " (" + Theme.formatDateTime(s.startTime()) + ")");
        }
    }

    public void refreshPreview() {
        try {
            LocalDate fromDate = parseDate(txtFromDate.getText(), "Từ ngày");
            LocalDate toDate = parseDate(txtToDate.getText(), "Đến ngày");

            Integer branchId = null;
            String branchLabel = "Tất cả chi nhánh";
            int bIdx = cboBranch.getSelectedIndex();
            if (bIdx > 0 && bIdx - 1 < branches.size()) {
                CinemaBranch b = branches.get(bIdx - 1);
                branchId = b.branchId();
                branchLabel = b.branchCode() + " - " + b.branchName();
            }

            Integer screeningId = null;
            String screeningLabel = "Tất cả suất chiếu";
            int sIdx = cboScreening.getSelectedIndex();
            if (sIdx > 0 && sIdx - 1 < screenings.size()) {
                ScreeningDetailView s = screenings.get(sIdx - 1);
                screeningId = s.screeningId();
                screeningLabel = "#" + s.screeningId() + " - " + s.movieTitle();
            }

            Integer selectedBranchId = branchId;
            String selectedBranchLabel = branchLabel;
            Integer selectedScreeningId = screeningId;
            String selectedScreeningLabel = screeningLabel;
            currentReportData = UiTask.call(this, "Đang tổng hợp báo cáo bán vé…", () -> service.generateReport(
                    fromDate, toDate, selectedBranchId, selectedBranchLabel, selectedScreeningId, selectedScreeningLabel));

            lblKpiScreenings.setText(currentReportData.totalScreenings() + " suất");
            lblKpiSoldVsCap.setText(currentReportData.totalTicketsSold() + " / " + currentReportData.totalCapacity() + " ghế");
            lblKpiOccupancy.setText(currentReportData.averageOccupancyPct().toPlainString() + "%");
            lblKpiRevenue.setText(Theme.formatCurrency(currentReportData.totalSalesRevenue()));

            tableModel.setRowCount(0);
            for (TicketSalesReportData.ScreeningSalesRow r : currentReportData.rows()) {
                tableModel.addRow(new Object[]{
                        "#" + r.screeningId(),
                        Theme.formatDate(r.screeningDate()),
                        Theme.formatDateTime(r.startTime()),
                        Theme.formatDateTime(r.endTime()),
                        r.branchName(),
                        r.roomName(),
                        r.roomType(),
                        r.movieTitle(),
                        r.roomCapacity(),
                        r.ticketsSold(),
                        r.availableSeats(),
                        r.occupancyRatePct().toPlainString() + "%",
                        Theme.formatCurrency(r.salesRevenue())
                });
            }

            tableModel.addRow(new Object[]{
                    "Tổng cộng",
                    currentReportData.totalScreenings() + " suất",
                    "-",
                    "-",
                    branchLabel,
                    "-",
                    "-",
                    "-",
                    currentReportData.totalCapacity(),
                    currentReportData.totalTicketsSold(),
                    currentReportData.totalAvailableSeats(),
                    currentReportData.averageOccupancyPct().toPlainString() + "%",
                    Theme.formatCurrency(currentReportData.totalSalesRevenue())
            });

            statusBanner.showSuccess("Đã cập nhật bản xem trước.");
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Không thể tạo báo cáo bán vé: " + ex.getMessage());
        }
    }

    public Path exportDefaultPdf() {
        return exportPdfTo(Path.of("reports", "exports", "BaoCaoBanVe_Mau.pdf").toAbsolutePath());
    }

    public Path exportPdfTo(Path targetPath) {
        if (currentReportData == null) {
            refreshPreview();
        }
        if (currentReportData == null) {
            throw new BusinessRuleException("Chưa có báo cáo hợp lệ để xuất. Vui lòng kiểm tra kỳ báo cáo và thử lại.");
        }
        Path absPath = targetPath.toAbsolutePath();
        TicketSalesReportData report = currentReportData;
        UiTask.run(this, "Đang xuất PDF…", () -> service.exportToPdf(report, absPath));
        statusBanner.showSuccess("Đã xuất PDF: " + absPath);
        return absPath;
    }

    private LocalDate parseDate(String text, String label) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException(label + " không đúng định dạng chuẩn yyyy-MM-dd.");
        }
    }

    public TicketSalesReportData getCurrentReportData() {
        return currentReportData;
    }

    public void setDateRangeForVerification(String fromStr, String toStr) {
        txtFromDate.setText(fromStr);
        txtToDate.setText(toStr);
    }

    public StatusBanner getStatusBanner() {
        return statusBanner;
    }
}
