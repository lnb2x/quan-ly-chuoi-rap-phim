package com.cinema.feature.report.revenue.ui;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.RevenueReportData;
import com.cinema.feature.report.revenue.RevenueReportService;
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
 * Màn hình Báo cáo 1: Báo cáo Doanh thu Chuỗi rạp theo Kỳ hạn và Chi nhánh (Screen 4 - R7).
 * Người lập báo cáo: Kế toán chuỗi rạp.
 * Người nhận báo cáo: Giám đốc Kinh doanh chuỗi rạp.
 * Hỗ trợ xem trước trực tiếp trên màn hình (Preview) và kết xuất ra tệp PDF tiếng Việt.
 */
public final class RevenueReportPanel extends JPanel {

    private final RevenueReportService service;
    private final StatusBanner statusBanner = new StatusBanner();

    private final JTextField txtFromDate = new JTextField("2026-11-01", 10);
    private final JTextField txtToDate = new JTextField("2026-11-15", 10);
    private final JComboBox<String> cboBranch = new JComboBox<>();

    private final JLabel lblKpiBookings = new JLabel("0 đơn");
    private final JLabel lblKpiTickets = new JLabel("0 vé");
    private final JLabel lblKpiRevenue = new JLabel("0 VNĐ");
    private final JLabel lblMetaInfo = new JLabel();

    private final DefaultTableModel branchTableModel;
    private final DefaultTableModel dailyTableModel;
    private List<CinemaBranch> branches = new ArrayList<>();
    private RevenueReportData currentReportData;

    public RevenueReportPanel(RevenueReportService service) {
        super(new BorderLayout(12, 12));
        this.service = service;
        setBackground(Theme.BG_APP);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Top Header
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setOpaque(false);
        JLabel title = new JLabel("BÁO CÁO DOANH THU CHUỖI RẠP (REVENUE REPORT — PREVIEW & PDF EXPORT)");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(statusBanner, BorderLayout.SOUTH);

        JPanel filterCard = buildFilterCard();

        JPanel kpiRow = new JPanel(new GridLayout(1, 3, 12, 0));
        kpiRow.setOpaque(false);
        kpiRow.add(Theme.createKpiCard("Tổng số Đơn đã thanh toán (SUCCESS)", lblKpiBookings, Theme.ACCENT_PRIMARY));
        kpiRow.add(Theme.createKpiCard("Tổng số Vé hợp lệ (VALID)", lblKpiTickets, Theme.ACCENT_TEAL));
        kpiRow.add(Theme.createKpiCard("Tổng Doanh thu Thực thu", lblKpiRevenue, Theme.ACCENT_SUCCESS));

        JPanel northContainer = new JPanel(new BorderLayout(10, 10));
        northContainer.setOpaque(false);
        northContainer.add(topPanel, BorderLayout.NORTH);
        northContainer.add(filterCard, BorderLayout.CENTER);
        northContainer.add(kpiRow, BorderLayout.SOUTH);

        // Preview tables
        String[] branchCols = {
                "Mã CN", "Tên Chi nhánh Rạp", "Thành phố",
                "Số đơn đã thanh toán", "Số vé hợp lệ", "Doanh thu thực thu (VNĐ)"
        };
        branchTableModel = new DefaultTableModel(branchCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable branchTable = new JTable(branchTableModel);
        branchTable.setName("revenueBranchTable");
        Theme.styleTable(branchTable);

        String[] dailyCols = {
                "Ngày thanh toán", "Mã CN", "Tên Chi nhánh Rạp",
                "Số đơn đã thanh toán", "Số vé hợp lệ", "Doanh thu trong ngày (VNĐ)"
        };
        dailyTableModel = new DefaultTableModel(dailyCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable dailyTable = new JTable(dailyTableModel);
        dailyTable.setName("revenueDailyTable");
        Theme.styleTable(dailyTable);

        JPanel branchCard = Theme.createCardPanel(new BorderLayout(6, 6));
        lblMetaInfo.setFont(Theme.FONT_BOLD);
        lblMetaInfo.setForeground(Theme.ACCENT_PRIMARY);
        JLabel lblSec1 = new JLabel("I. Bảng Tổng hợp Doanh thu theo Chi nhánh Rạp:");
        lblSec1.setFont(Theme.FONT_SUBTITLE);
        JPanel sec1Header = new JPanel(new BorderLayout());
        sec1Header.setOpaque(false);
        sec1Header.add(lblSec1, BorderLayout.WEST);
        sec1Header.add(lblMetaInfo, BorderLayout.EAST);
        branchCard.add(sec1Header, BorderLayout.NORTH);
        branchCard.add(new JScrollPane(branchTable), BorderLayout.CENTER);

        JPanel dailyCard = Theme.createCardPanel(new BorderLayout(6, 6));
        JLabel lblSec2 = new JLabel("II. Bảng Chi tiết Doanh thu theo Ngày và Chi nhánh:");
        lblSec2.setFont(Theme.FONT_SUBTITLE);
        dailyCard.add(lblSec2, BorderLayout.NORTH);
        dailyCard.add(new JScrollPane(dailyTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, branchCard, dailyCard);
        splitPane.setResizeWeight(0.45);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        add(northContainer, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        loadBranchFilter();
        refreshPreview();
    }

    private JPanel buildFilterCard() {
        JPanel card = Theme.createCardPanel(new BorderLayout(8, 6));
        JPanel metaBanner = new JPanel(new BorderLayout());
        metaBanner.setOpaque(false);
        JLabel roleInfo = new JLabel(
                "Người lập báo cáo: Kế toán Chuỗi rạp (Trần Thị Thu Hà)   |   Người nhận báo cáo: Giám đốc Kinh doanh Chuỗi rạp");
        roleInfo.setFont(Theme.FONT_BOLD);
        roleInfo.setForeground(new Color(15, 118, 110));
        metaBanner.add(roleInfo, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        controls.setOpaque(false);
        controls.add(new JLabel("Từ ngày (yyyy-MM-dd):"));
        controls.add(txtFromDate);
        controls.add(new JLabel("Đến ngày (yyyy-MM-dd):"));
        controls.add(txtToDate);
        controls.add(new JLabel("Chi nhánh rạp:"));
        controls.add(cboBranch);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        actions.setOpaque(false);

        JButton btnPreview = Theme.createPrimaryButton("Xem trước Báo cáo (Preview)");
        btnPreview.setName("btnPreviewRevenue");
        btnPreview.addActionListener(e -> refreshPreview());

        JButton btnExportPdf = Theme.createSuccessButton("Xuất Báo cáo ra PDF");
        btnExportPdf.setName("btnExportRevenuePdf");
        btnExportPdf.addActionListener(e -> exportDefaultPdf());

        actions.add(btnPreview);
        actions.add(btnExportPdf);

        card.add(metaBanner, BorderLayout.NORTH);
        card.add(controls, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void loadBranchFilter() {
        branches = service.listBranches();
        cboBranch.removeAllItems();
        cboBranch.addItem("Tất cả chi nhánh");
        for (CinemaBranch b : branches) {
            cboBranch.addItem(b.branchCode() + " - " + b.branchName());
        }
    }

    public void refreshPreview() {
        try {
            LocalDate fromDate = parseDate(txtFromDate.getText(), "Từ ngày");
            LocalDate toDate = parseDate(txtToDate.getText(), "Đến ngày");
            Integer branchId = null;
            String branchLabel = "Tất cả chi nhánh";
            int idx = cboBranch.getSelectedIndex();
            if (idx > 0 && idx - 1 < branches.size()) {
                CinemaBranch b = branches.get(idx - 1);
                branchId = b.branchId();
                branchLabel = b.branchCode() + " - " + b.branchName();
            }

            currentReportData = service.generateReport(fromDate, toDate, branchId, branchLabel);

            lblKpiBookings.setText(currentReportData.totalPaidBookings() + " đơn");
            lblKpiTickets.setText(currentReportData.totalValidTickets() + " vé");
            lblKpiRevenue.setText(Theme.formatCurrency(currentReportData.totalPaidRevenue()));
            lblMetaInfo.setText(String.format("Kỳ: %s - %s | %s",
                    Theme.formatDate(fromDate), Theme.formatDate(toDate), branchLabel));

            branchTableModel.setRowCount(0);
            for (RevenueReportData.BranchRevenueRow r : currentReportData.branchSummaries()) {
                branchTableModel.addRow(new Object[]{
                        r.branchCode(),
                        r.branchName(),
                        r.city(),
                        r.paidBookings(),
                        r.validTickets(),
                        Theme.formatCurrency(r.paidRevenue())
                });
            }
            branchTableModel.addRow(new Object[]{
                    "TỔNG CỘNG",
                    "TỔNG DOANH THU TOÀN CHUỖI",
                    "-",
                    currentReportData.totalPaidBookings(),
                    currentReportData.totalValidTickets(),
                    Theme.formatCurrency(currentReportData.totalPaidRevenue())
            });

            dailyTableModel.setRowCount(0);
            for (RevenueReportData.DailyRevenueRow d : currentReportData.dailyDetails()) {
                dailyTableModel.addRow(new Object[]{
                        Theme.formatDate(d.revenueDate()),
                        d.branchCode(),
                        d.branchName(),
                        d.paidBookings(),
                        d.validTickets(),
                        Theme.formatCurrency(d.paidRevenue())
                });
            }

            statusBanner.showSuccess(String.format(
                    "Đã kết xuất bản xem trước Báo cáo Doanh thu: %d đơn thành công, %d vé hợp lệ, Tổng thực thu: %s.",
                    currentReportData.totalPaidBookings(),
                    currentReportData.totalValidTickets(),
                    Theme.formatCurrency(currentReportData.totalPaidRevenue())));
        } catch (BusinessRuleException bre) {
            statusBanner.showError(bre.getMessage());
        } catch (Exception ex) {
            statusBanner.showError("Lỗi khi tạo Báo cáo Doanh thu: " + ex.getMessage());
        }
    }

    public Path exportDefaultPdf() {
        return exportPdfTo(Path.of("reports", "exports", "BaoCaoDoanhThu_Mau.pdf").toAbsolutePath());
    }

    public Path exportPdfTo(Path targetPath) {
        if (currentReportData == null) {
            refreshPreview();
        }
        Path absPath = targetPath.toAbsolutePath();
        service.exportToPdf(currentReportData, absPath);
        statusBanner.showSuccess("Đã xuất thành công Báo cáo Doanh thu ra tệp PDF: " + absPath);
        return absPath;
    }

    private LocalDate parseDate(String text, String label) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException(label + " không đúng định dạng chuẩn yyyy-MM-dd.");
        }
    }

    public RevenueReportData getCurrentReportData() {
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
