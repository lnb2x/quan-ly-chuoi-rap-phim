package com.cinema.reporting;

import com.cinema.domain.RevenueReportData;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.shared.ui.Theme;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Bộ kết xuất Báo cáo PDF chuẩn tiếng Việt (sử dụng Apache PDFBox 3 và nhúng
 * phông chữ Unicode TrueType để hiển thị chính xác 100% dấu tiếng Việt).
 */
public final class PdfReportWriter {

    private static final String REGULAR_FONT_RESOURCE = "/fonts/vietnamese-regular.ttf";
    private static final String BOLD_FONT_RESOURCE = "/fonts/vietnamese-bold.ttf";

    public void exportRevenueReport(RevenueReportData data, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (PDDocument doc = new PDDocument()) {
            PDType0Font fontRegular = loadFont(doc, REGULAR_FONT_RESOURCE, Path.of("C:\\Windows\\Fonts\\arial.ttf"));
            PDType0Font fontBold = loadFont(doc, BOLD_FONT_RESOURCE, Path.of("C:\\Windows\\Fonts\\arialbd.ttf"));

            PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // A4 Landscape
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            float margin = 36f;
            float width = pageSize.getWidth() - 2 * margin;
            float y = pageSize.getHeight() - margin;

            PDPageContentStream cs = new PDPageContentStream(doc, page);

            // Header
            y = drawText(cs, fontBold, 14, margin, y, "HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM CINEVIET");
            y -= 4;
            y = drawText(cs, fontBold, 16, margin, y, "BÁO CÁO DOANH THU CHUỖI RẠP THEO KỲ HẠN VÀ CHI NHÁNH");
            y -= 6;
            y = drawText(cs, fontRegular, 10, margin, y,
                    String.format("Kỳ báo cáo: Từ ngày %s đến ngày %s   |   Phạm vi chi nhánh: %s",
                            Theme.formatDate(data.fromDate()),
                            Theme.formatDate(data.toDate()),
                            data.branchFilterLabel()));
            y = drawText(cs, fontRegular, 10, margin, y,
                    String.format("Người lập báo cáo: %s (%s)   |   Người nhận báo cáo: %s   |   Thời điểm lập: %s",
                            data.creatorName(),
                            data.creatorTitle(),
                            data.recipientTitle(),
                            Theme.formatDateTime(data.generatedAt())));
            y -= 6;
            drawHorizontalLine(cs, margin, y, width, new Color(14, 116, 144));
            y -= 16;

            // KPI Summary Box
            y = drawText(cs, fontBold, 11, margin, y,
                    String.format("TỔNG HỢP TOÀN KỲ:   Tổng số đơn đã thanh toán: %d đơn   |   Tổng số vé hợp lệ: %d vé   |   TỔNG DOANH THU THỰC THU: %s",
                            data.totalPaidBookings(),
                            data.totalValidTickets(),
                            Theme.formatCurrency(data.totalPaidRevenue())));
            y = drawText(cs, fontRegular, 9, margin, y,
                    "Quy tắc ghi nhận doanh thu: Chỉ tính vé hợp lệ (VALID) thuộc các đơn đã xác nhận (CONFIRMED) và có giao dịch thanh toán thành công (SUCCESS).");
            y -= 10;

            // Section 1: Branch Summary Table
            y = drawText(cs, fontBold, 11, margin, y, "I. TỔNG HỢP DOANH THU THEO CHI NHÁNH RẠP");
            y -= 4;

            float[] branchCols = {65f, 185f, 110f, 110f, 110f, 185f};
            String[] branchHeaders = {"Mã CN", "Tên Chi nhánh Rạp", "Thành phố", "Số đơn TT", "Số vé hợp lệ", "Doanh thu thực thu"};
            y = drawTableRow(cs, fontBold, 9.5f, margin, y, branchCols, branchHeaders, new Color(224, 242, 254));

            for (RevenueReportData.BranchRevenueRow row : data.branchSummaries()) {
                String[] cells = {
                        row.branchCode(),
                        truncate(row.branchName(), 34),
                        row.city(),
                        String.valueOf(row.paidBookings()),
                        String.valueOf(row.validTickets()),
                        Theme.formatCurrency(row.paidRevenue())
                };
                y = drawTableRow(cs, fontRegular, 9.5f, margin, y, branchCols, cells, null);
            }

            String[] totalBranchRow = {
                    "TỔNG",
                    "TỔNG CỘNG TOÀN CHUỖI",
                    "-",
                    String.valueOf(data.totalPaidBookings()),
                    String.valueOf(data.totalValidTickets()),
                    Theme.formatCurrency(data.totalPaidRevenue())
            };
            y = drawTableRow(cs, fontBold, 9.5f, margin, y, branchCols, totalBranchRow, new Color(241, 245, 249));
            y -= 12;

            // Section 2: Daily Revenue Detail Table
            y = drawText(cs, fontBold, 11, margin, y, "II. CHI TIẾT DOANH THU THEO NGÀY VÀ CHI NHÁNH");
            y -= 4;

            float[] dailyCols = {90f, 80f, 220f, 110f, 110f, 155f};
            String[] dailyHeaders = {"Ngày TT", "Mã CN", "Tên Chi nhánh Rạp", "Số đơn TT", "Số vé hợp lệ", "Doanh thu trong ngày"};
            y = drawTableRow(cs, fontBold, 9.5f, margin, y, dailyCols, dailyHeaders, new Color(224, 242, 254));

            for (RevenueReportData.DailyRevenueRow d : data.dailyDetails()) {
                if (y < margin + 70f) {
                    cs.close();
                    page = new PDPage(pageSize);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageSize.getHeight() - margin;
                    y = drawText(cs, fontBold, 10, margin, y, "II. CHI TIẾT DOANH THU THEO NGÀY VÀ CHI NHÁNH (Tiếp theo)");
                    y -= 4;
                    y = drawTableRow(cs, fontBold, 9.5f, margin, y, dailyCols, dailyHeaders, new Color(224, 242, 254));
                }
                String[] cells = {
                        Theme.formatDate(d.revenueDate()),
                        d.branchCode(),
                        truncate(d.branchName(), 38),
                        String.valueOf(d.paidBookings()),
                        String.valueOf(d.validTickets()),
                        Theme.formatCurrency(d.paidRevenue())
                };
                y = drawTableRow(cs, fontRegular, 9.5f, margin, y, dailyCols, cells, null);
            }

            y -= 18;
            if (y < margin + 55f) {
                cs.close();
                page = new PDPage(pageSize);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                y = pageSize.getHeight() - margin;
            }
            drawSignatureFooter(cs, fontBold, fontRegular, margin, y, width,
                    "NGƯỜI LẬP BÁO CÁO (" + data.creatorTitle().toUpperCase() + ")",
                    data.creatorName(),
                    "NGƯỜI NHẬN BÁO CÁO (" + data.recipientTitle().toUpperCase() + ")",
                    "Ban Giám đốc Kinh doanh Chuỗi rạp CineViet");

            cs.close();
            doc.save(outputPath.toFile());
        }
    }

    public void exportTicketSalesReport(TicketSalesReportData data, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (PDDocument doc = new PDDocument()) {
            PDType0Font fontRegular = loadFont(doc, REGULAR_FONT_RESOURCE, Path.of("C:\\Windows\\Fonts\\arial.ttf"));
            PDType0Font fontBold = loadFont(doc, BOLD_FONT_RESOURCE, Path.of("C:\\Windows\\Fonts\\arialbd.ttf"));

            PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // A4 Landscape
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            float margin = 36f;
            float width = pageSize.getWidth() - 2 * margin;
            float y = pageSize.getHeight() - margin;

            PDPageContentStream cs = new PDPageContentStream(doc, page);

            // Header
            y = drawText(cs, fontBold, 14, margin, y, "HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM CINEVIET");
            y -= 4;
            y = drawText(cs, fontBold, 16, margin, y, "BÁO CÁO TÌNH HÌNH BÁN VÉ VÀ TỶ LỆ LẤP ĐẦY THEO SUẤT CHIẾU");
            y -= 6;
            y = drawText(cs, fontRegular, 9.0f, margin, y,
                    String.format("Kỳ báo cáo: Từ ngày %s đến ngày %s  |  Chi nhánh: %s  |  Suất chiếu: %s",
                            Theme.formatDate(data.fromDate()),
                            Theme.formatDate(data.toDate()),
                            data.branchFilterLabel(),
                            data.screeningFilterLabel()));
            y = drawText(cs, fontRegular, 9.0f, margin, y,
                    String.format("Người lập báo cáo: %s (%s)  |  Người nhận báo cáo: %s  |  Thời điểm lập: %s",
                            data.creatorName(),
                            data.creatorTitle(),
                            data.recipientTitle(),
                            Theme.formatDateTime(data.generatedAt())));
            y -= 6;
            drawHorizontalLine(cs, margin, y, width, new Color(13, 148, 136));
            y -= 16;

            y = drawText(cs, fontBold, 9.2f, margin, y,
                    String.format("TỔNG HỢP: Số suất chiếu: %d suất | Tổng sức chứa: %d ghế | Vé đã bán: %d vé | Ghế trống: %d ghế | Lấp đầy TB: %s%% | Doanh thu: %s",
                            data.totalScreenings(),
                            data.totalCapacity(),
                            data.totalTicketsSold(),
                            data.totalAvailableSeats(),
                            data.averageOccupancyPct().toPlainString(),
                            Theme.formatCurrency(data.totalSalesRevenue())));
            y -= 8;

            float[] cols = {38f, 88f, 112f, 90f, 135f, 48f, 46f, 50f, 50f, 112f};
            String[] headers = {"Mã SC", "Bắt đầu", "Chi nhánh", "Phòng", "Tựa đề Phim", "Sức chứa", "Đã bán", "Còn trống", "Lấp đầy", "Doanh thu"};
            y = drawTableRow(cs, fontBold, 8.8f, margin, y, cols, headers, new Color(224, 242, 254));

            List<TicketSalesReportData.ScreeningSalesRow> rows = data.rows();
            for (TicketSalesReportData.ScreeningSalesRow r : rows) {
                if (y < margin + 70f) {
                    cs.close();
                    page = new PDPage(pageSize);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageSize.getHeight() - margin;
                    y = drawText(cs, fontBold, 10, margin, y, "BÁO CÁO TÌNH HÌNH BÁN VÉ VÀ TỶ LỆ LẤP ĐẦY (Tiếp theo)");
                    y -= 4;
                    y = drawTableRow(cs, fontBold, 8.8f, margin, y, cols, headers, new Color(224, 242, 254));
                }
                String[] cells = {
                        "#" + r.screeningId(),
                        Theme.formatDateTime(r.startTime()),
                        truncate(r.branchName(), 19),
                        truncate(r.roomName(), 16),
                        truncate(r.movieTitle(), 25),
                        String.valueOf(r.roomCapacity()),
                        String.valueOf(r.ticketsSold()),
                        String.valueOf(r.availableSeats()),
                        r.occupancyRatePct().toPlainString() + "%",
                        Theme.formatCurrency(r.salesRevenue())
                };
                y = drawTableRow(cs, fontRegular, 8.5f, margin, y, cols, cells, null);
            }

            String[] totalRow = {
                    "TỔNG",
                    data.totalScreenings() + " suất",
                    "-",
                    "-",
                    "TỔNG CỘNG KỲ BÁO CÁO",
                    String.valueOf(data.totalCapacity()),
                    String.valueOf(data.totalTicketsSold()),
                    String.valueOf(data.totalAvailableSeats()),
                    data.averageOccupancyPct().toPlainString() + "%",
                    Theme.formatCurrency(data.totalSalesRevenue())
            };
            y = drawTableRow(cs, fontBold, 8.8f, margin, y, cols, totalRow, new Color(241, 245, 249));

            y -= 18;
            if (y < margin + 55f) {
                cs.close();
                page = new PDPage(pageSize);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                y = pageSize.getHeight() - margin;
            }
            drawSignatureFooter(cs, fontBold, fontRegular, margin, y, width,
                    "NGƯỜI LẬP BÁO CÁO (" + data.creatorTitle().toUpperCase() + ")",
                    data.creatorName(),
                    "NGƯỜI NHẬN BÁO CÁO (" + data.recipientTitle().toUpperCase() + ")",
                    "Ban Điều hành & Quản lý Kinh doanh CineViet");

            cs.close();
            doc.save(outputPath.toFile());
        }
    }

    private PDType0Font loadFont(PDDocument doc, String resourcePath, Path fallbackSystemPath) throws IOException {
        try (InputStream in = PdfReportWriter.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                return PDType0Font.load(doc, in, true);
            }
        }
        return PDType0Font.load(doc, fallbackSystemPath.toFile());
    }

    private float drawText(PDPageContentStream cs, PDType0Font font, float fontSize, float x, float y, String text) throws IOException {
        cs.setNonStrokingColor(new Color(15, 23, 42));
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizePdfText(text));
        cs.endText();
        return y - (fontSize + 5f);
    }

    private void drawHorizontalLine(PDPageContentStream cs, float x, float y, float width, Color color) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(1.2f);
        cs.moveTo(x, y);
        cs.lineTo(x + width, y);
        cs.stroke();
    }

    private float drawTableRow(PDPageContentStream cs, PDType0Font font, float fontSize, float x, float y,
                               float[] colWidths, String[] values, Color bgColor) throws IOException {
        float rowHeight = fontSize + 9f;
        float totalW = 0f;
        for (float w : colWidths) {
            totalW += w;
        }
        float bottomY = y - rowHeight + 4f;

        if (bgColor != null) {
            cs.setNonStrokingColor(bgColor);
            cs.addRect(x, bottomY, totalW, rowHeight);
            cs.fill();
        }

        cs.setStrokingColor(new Color(203, 213, 225));
        cs.setLineWidth(0.5f);
        cs.addRect(x, bottomY, totalW, rowHeight);
        cs.stroke();

        float curX = x;
        for (int i = 0; i < colWidths.length; i++) {
            String cellText = i < values.length && values[i] != null ? values[i] : "";
            cs.setNonStrokingColor(new Color(15, 23, 42));
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(curX + 4f, bottomY + 5f);
            cs.showText(sanitizePdfText(cellText));
            cs.endText();
            curX += colWidths[i];
            if (i < colWidths.length - 1) {
                cs.moveTo(curX, bottomY);
                cs.lineTo(curX, bottomY + rowHeight);
                cs.stroke();
            }
        }
        return y - rowHeight;
    }

    private void drawSignatureFooter(PDPageContentStream cs, PDType0Font fontBold, PDType0Font fontRegular,
                                     float x, float y, float width,
                                     String leftRole, String leftName,
                                     String rightRole, String rightName) throws IOException {
        float rightX = x + width * 0.55f;
        drawText(cs, fontBold, 9.5f, x + 20f, y, leftRole);
        drawText(cs, fontBold, 9.5f, rightX, y, rightRole);
        drawText(cs, fontRegular, 9f, x + 20f, y - 14f, "(Ký, ghi rõ họ tên)");
        drawText(cs, fontRegular, 9f, rightX, y - 14f, "(Ký, đóng dấu xác nhận)");
        drawText(cs, fontBold, 10f, x + 20f, y - 38f, leftName);
        drawText(cs, fontBold, 10f, rightX, y - 38f, rightName);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    private static String sanitizePdfText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", " ").replace("\n", " ");
    }
}
