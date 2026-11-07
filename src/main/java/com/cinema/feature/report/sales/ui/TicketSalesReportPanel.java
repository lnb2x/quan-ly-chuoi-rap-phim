package com.cinema.feature.report.sales.ui;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.report.sales.TicketSalesReportService;
import com.cinema.shared.ui.StatusBanner;
import com.cinema.shared.ui.Theme;
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
 * Màn hình Báo cáo 2: Báo cáo Tình hình Bán vé và Tỷ lệ Lấp đầy theo Suất chiếu (Screen 5 - R7).
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
        JLabel title = new JLabel("BÁO CÁO TÌNH HÌNH BÁN VÉ & TỶ LỆ LẤP ĐẦY (TICKET SALES REPORT)");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(statusBanner, BorderLayout.SOUTH);

        JPanel filterCard = buildFilterCard();

        JPanel kpiRow = new JPanel(new GridLayout(1, 4, 12, 0));
        kpiRow.setOpaque(false);
        kpiRow.add(Theme.createKpiCard("Tổng số Suất chiếu", lblKpiScreenings, Theme.ACCENT_PRIMARY));
        kpiRow.add(Theme.createKpiCard("Vé đã bán / Tổng sức chứa", lblKpiSoldVsCap, Theme.ACCENT_TEAL));
        kpiRow.add(Theme.createKpiCard("Tỷ lệ Lấp đầy Trung bình", lblKpiOccupancy, Theme.ACCENT_WARNING));
        kpiRow.add(Theme.createKpiCard("Tổng Doanh thu Bán vé", lblKpiRevenue, Theme.ACCENT_SUCCESS));

        JPanel northContainer = new JPanel(new BorderLayout(10, 10));
        northContainer.setOpaque(false);
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(filterCard, BorderLayout.CENTER);
        northContainer.add(kpiRow, BorderLayout.SOUTH);

        String[] cols = {
                "Mã SC", "Ngày chiếu", "Bắt đầu", "Kết thúc", "Chi nhánh", "Phòng chiếu",
                "Loại", "Tựa đề Phim", "Sức chứa", "Vé đã bán", "Ghế trống", "Tỷ lệ lấp đầy", "Doanh thu bán vé"
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
        JLabel lblTable = new JLabel("Bảng Xem trước Chi tiết Tình hình Bán vé, Sức chứa, Ghế trống & Doanh thu theo Suất chiếu:");
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
                "Người lập báo cáo: Quản lý Chi nhánh (Nguyễn Minh Hoàng)   |   Người nhận báo cáo: Ban Điều hành và Quản lý Kinh doanh");
        roleInfo.setFont(Theme.FONT_BOLD);
        roleInfo.setForeground(new Color(3, 105, 161));
        metaBanner.add(roleInfo, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        controls.setOpaque(false);
        controls.add(new JLabel("Từ ngày:"));
        controls.add(txtFromDate);
        controls.add(new JLabel("Đến ngày:"));
        controls.add(txtToDate);
        controls.add(new JLabel("Chi nhánh:"));
        cboBranch.addActionListener(e -> reloadScreeningFilter());
        controls.add(cboBranch);
        controls.add(new JLabel("Suất chiếu:"));
        controls.add(cboScreening);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        actions.setOpaque(false);

        JButton btnPreview = Theme.createPrimaryButton("Xem trước Báo cáo (Preview)");
        btnPreview.setName("btnPreviewTicketSales");
        btnPreview.addActionListener(e -> refreshPreview());

        JButton btnExportPdf = Theme.createSuccessButton("Xuất Báo cáo ra PDF");
        btnExportPdf.setName("btnExportTicketSalesPdf");
        btnExportPdf.addActionListener(e -> exportDefaultPdf());

        actions.add(btnPreview);
        actions.add(btnExportPdf);

        card.add(metaBanner, BorderLayout.NORTH);
        card.add(controls, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void loadFilterOptions() {
        branches = service.listBranches();
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
        screenings = service.listScreeningsForBranch(branchId);
        cboScreening.removeAllItems();
        cboScreening.addItem("Tất cả các suất chiếu");
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

            currentReportData = service.generateReport(
                    fromDate, toDate, branchId, branchLabel, screeningId, screeningLabel);

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
                    "TỔNG CỘNG",
                    currentReportData.totalScreenings() + " suất",
                    "-",
                    "-",
                    branchLabel,
                    "-",
                    "-",
                    "TỔNG HỢP TOÀN KỲ",
                    currentReportData.totalCapacity(),
                    currentReportData.totalTicketsSold(),
                    currentReportData.totalAvailableSeats(),
                    currentReportData.averageOccupancyPct().toPlainString() + "%",
                    Theme.formatCurrency(currentReportData.totalSalesRevenue())
            });

            statusBanner.showSuccess(String.format(
                    "Đã kết xuất bản xem trước Báo cáo Bán vé: %d suất chiếu, %d/%d vé bán ra (Lấp đầy TB: %s%%), Doanh thu: %s.",
                    currentReportData.totalScreenings(),
                    currentReportData.totalTicketsSold(),
                    currentReportData.totalCapacity(),
                    currentReportData.averageOccupancyPct().toPlainString(),
                    Theme.formatCurrency(currentReportData.totalSalesRevenue())));
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi tạo Báo cáo Tình hình Bán vé: " + ex.getMessage());
        }
    }

    public Path exportDefaultPdf() {
        if (currentReportData == null) {
            refreshPreview();
        }
        Path targetPath = Path.of("reports", "exports", "BaoCaoBanVe_Mau.pdf").toAbsolutePath();
        service.exportToPdf(currentReportData, targetPath);
        statusBanner.showSuccess("Đã xuất thành công Báo cáo Tình hình Bán vé ra tệp PDF: " + targetPath);
        return targetPath;
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
