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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Xuất PDF tiếng Việt, tự xuống dòng và phân trang theo kích thước chữ thực tế. */
public final class PdfReportWriter {
    public void exportRevenueReport(RevenueReportData data, Path outputPath) throws IOException {
        try (PDDocument doc = new PDDocument(); Layout page = new Layout(doc)) {
            page.heading("BÁO CÁO DOANH THU CHUỖI RẠP THEO KỲ HẠN VÀ CHI NHÁNH");
            page.text("Kỳ báo cáo: " + Theme.formatDate(data.fromDate()) + " - " + Theme.formatDate(data.toDate())
                    + " | Phạm vi: " + data.branchFilterLabel(), 10, false);
            page.metadata(data.creatorName(), data.creatorTitle(), data.recipientTitle(), Theme.formatDateTime(data.generatedAt()));
            page.text("TỔNG HỢP TOÀN KỲ: " + data.totalPaidBookings() + " đơn đã thanh toán | "
                    + data.totalValidTickets() + " vé hợp lệ | Doanh thu thực thu: "
                    + Theme.formatCurrency(data.totalPaidRevenue()), 11, true);
            page.text("Chỉ tính vé VALID thuộc đơn CONFIRMED có thanh toán SUCCESS; không tính đơn chờ, thất bại hoặc đã hoàn tiền.", 9, false);
            List<TableRow> branches = new ArrayList<>();
            for (var r : data.branchSummaries()) {
                branches.add(new TableRow(new String[]{r.branchCode(), r.branchName(), r.city(),
                        Integer.toString(r.paidBookings()), Integer.toString(r.validTickets()), Theme.formatCurrency(r.paidRevenue())}, false));
            }
            branches.add(new TableRow(new String[]{"TỔNG", "TỔNG CỘNG TOÀN CHUỖI", "-", Integer.toString(data.totalPaidBookings()),
                    Integer.toString(data.totalValidTickets()), Theme.formatCurrency(data.totalPaidRevenue())}, true));
            page.table("I. TỔNG HỢP DOANH THU THEO CHI NHÁNH RẠP", new float[]{65, 220, 100, 90, 90, 200},
                    new String[]{"Mã CN", "Tên chi nhánh rạp", "Thành phố", "Số đơn TT", "Số vé hợp lệ", "Doanh thu thực thu"}, branches, 9);
            List<TableRow> daily = new ArrayList<>();
            for (var r : data.dailyDetails()) {
                daily.add(new TableRow(new String[]{Theme.formatDate(r.revenueDate()), r.branchCode(), r.branchName(),
                        Integer.toString(r.paidBookings()), Integer.toString(r.validTickets()), Theme.formatCurrency(r.paidRevenue())}, false));
            }
            page.table("II. CHI TIẾT DOANH THU THEO NGÀY VÀ CHI NHÁNH", new float[]{85, 65, 235, 90, 90, 200},
                    new String[]{"Ngày thanh toán", "Mã CN", "Tên chi nhánh rạp", "Số đơn TT", "Số vé hợp lệ", "Doanh thu trong ngày"}, daily, 9);
            page.signature(data.creatorTitle(), data.creatorName(), data.recipientTitle());
            page.finish();
            saveSafely(doc, outputPath);
        }
    }

    public void exportTicketSalesReport(TicketSalesReportData data, Path outputPath) throws IOException {
        try (PDDocument doc = new PDDocument(); Layout page = new Layout(doc)) {
            page.heading("BÁO CÁO TÌNH HÌNH BÁN VÉ VÀ TỶ LỆ LẤP ĐẦY THEO SUẤT CHIẾU");
            page.text("Kỳ báo cáo: " + Theme.formatDate(data.fromDate()) + " - " + Theme.formatDate(data.toDate())
                    + " | Chi nhánh: " + data.branchFilterLabel() + " | Suất chiếu: " + data.screeningFilterLabel(), 10, false);
            page.metadata(data.creatorName(), data.creatorTitle(), data.recipientTitle(), Theme.formatDateTime(data.generatedAt()));
            page.text("TỔNG HỢP: " + data.totalScreenings() + " suất | Sức chứa: " + data.totalCapacity()
                    + " ghế | Đã bán: " + data.totalTicketsSold() + " vé | Ghế trống: " + data.totalAvailableSeats()
                    + " | Lấp đầy: " + data.averageOccupancyPct().toPlainString() + "% | Doanh thu: "
                    + Theme.formatCurrency(data.totalSalesRevenue()), 10, true);
            page.text("Vé đã bán và doanh thu chỉ tính đơn CONFIRMED có SUCCESS. Ghế trống loại cả ghế đang được đơn PENDING giữ.", 9, false);
            List<TableRow> rows = new ArrayList<>();
            for (var r : data.rows()) {
                rows.add(new TableRow(new String[]{"#" + r.screeningId(), Theme.formatDateTime(r.startTime()), r.branchName(),
                        r.roomName(), r.movieTitle(), Integer.toString(r.roomCapacity()), Integer.toString(r.ticketsSold()),
                        Integer.toString(r.availableSeats()), r.occupancyRatePct().toPlainString() + "%", Theme.formatCurrency(r.salesRevenue())}, false));
            }
            rows.add(new TableRow(new String[]{"TỔNG", data.totalScreenings() + " suất", "-", "-", "TỔNG CỘNG KỲ BÁO CÁO",
                    Integer.toString(data.totalCapacity()), Integer.toString(data.totalTicketsSold()), Integer.toString(data.totalAvailableSeats()),
                    data.averageOccupancyPct().toPlainString() + "%", Theme.formatCurrency(data.totalSalesRevenue())}, true));
            page.table("CHI TIẾT TÌNH HÌNH BÁN VÉ THEO SUẤT CHIẾU", new float[]{40, 90, 112, 65, 150, 45, 45, 45, 55, 118},
                    new String[]{"Mã SC", "Bắt đầu", "Chi nhánh", "Phòng", "Tựa đề phim", "Sức chứa", "Đã bán", "Ghế trống", "Lấp đầy", "Doanh thu"}, rows, 8.5f);
            page.signature(data.creatorTitle(), data.creatorName(), data.recipientTitle());
            page.finish();
            saveSafely(doc, outputPath);
        }
    }

    private void saveSafely(PDDocument doc, Path outputPath) throws IOException {
        Path target = outputPath.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".cinema-report-", ".pdf");
        try {
            doc.save(temporary.toFile());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record TableRow(String[] cells, boolean total) { }

    private static final class Layout implements AutoCloseable {
        private static final float MARGIN = 36;
        private static final PDRectangle SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        private static final float WIDTH = SIZE.getWidth() - 2 * MARGIN;
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument document) throws IOException {
            this.document = document;
            regular = loadFont(document, "/fonts/vietnamese-regular.ttf");
            bold = loadFont(document, "/fonts/vietnamese-bold.ttf");
            newPage();
        }

        private static PDType0Font loadFont(PDDocument document, String resource) throws IOException {
            try (InputStream input = PdfReportWriter.class.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Thiếu phông chữ tiếng Việt đóng gói: " + resource);
                return PDType0Font.load(document, input, true);
            }
        }

        void heading(String title) throws IOException {
            text("HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM CINEVIET", 12, true);
            text(title, 14, true);
            y -= 6;
        }

        void metadata(String name, String role, String recipient, String generatedAt) throws IOException {
            text("Người lập báo cáo: " + name + " | " + role, 9, false);
            text("Người nhận báo cáo: " + recipient, 9, false);
            text("Thời điểm lập: " + generatedAt, 9, false);
            y -= 8;
        }

        void text(String value, float size, boolean strong) throws IOException {
            PDType0Font font = strong ? bold : regular;
            for (String line : wrap(value, font, size, WIDTH)) {
                if (y - size - 5 < MARGIN) newPage();
                draw(line, font, size, MARGIN, y - size);
                y -= size + 5;
            }
        }

        void table(String title, float[] weights, String[] headers, List<TableRow> rows, float size) throws IOException {
            float sum = 0;
            for (float weight : weights) sum += weight;
            float[] widths = new float[weights.length];
            for (int i = 0; i < weights.length; i++) widths[i] = weights[i] / sum * WIDTH;
            y -= 8;
            tableHeader(title, widths, headers, size);
            if (rows.isEmpty()) text("Không có dữ liệu trong kỳ báo cáo.", 9, false);
            for (TableRow row : rows) {
                List<List<String>> cells = cellLines(row.cells(), widths, row.total() ? bold : regular, size);
                int lines = cells.stream().mapToInt(List::size).max().orElse(1);
                int offset = 0;
                while (offset < lines) {
                    int available = (int) ((y - MARGIN - 8) / (size + 3));
                    if (available < 1 || (offset == 0 && lines <= 16 && lines > available)) {
                        newPage();
                        tableHeader(title + " (Tiếp theo)", widths, headers, size);
                        available = (int) ((y - MARGIN - 8) / (size + 3));
                    }
                    int count = Math.min(lines - offset, available);
                    drawRow(cells, widths, size, row.total() ? bold : regular, offset, count,
                            row.total() ? new Color(224, 242, 254) : Color.WHITE);
                    offset += count;
                }
            }
        }

        private void tableHeader(String title, float[] widths, String[] headers, float size) throws IOException {
            if (y < MARGIN + 100) newPage();
            text(title, 10, true);
            List<List<String>> cells = cellLines(headers, widths, bold, size);
            drawRow(cells, widths, size, bold, 0, cells.stream().mapToInt(List::size).max().orElse(1), new Color(224, 242, 254));
        }

        private List<List<String>> cellLines(String[] values, float[] widths, PDType0Font font, float size) throws IOException {
            List<List<String>> cells = new ArrayList<>();
            for (int i = 0; i < widths.length; i++) cells.add(wrap(values[i], font, size, widths[i] - 8));
            return cells;
        }

        private void drawRow(List<List<String>> cells, float[] widths, float size, PDType0Font font,
                             int offset, int count, Color background) throws IOException {
            float height = count * (size + 3) + 8;
            float x = MARGIN;
            stream.setNonStrokingColor(background);
            stream.addRect(x, y - height, WIDTH, height);
            stream.fill();
            stream.setStrokingColor(new Color(203, 213, 225));
            stream.setLineWidth(0.5f);
            for (int column = 0; column < widths.length; column++) {
                stream.addRect(x, y - height, widths[column], height);
                stream.stroke();
                List<String> lines = cells.get(column);
                for (int line = offset; line < Math.min(offset + count, lines.size()); line++) {
                    draw(lines.get(line), font, size, x + 4, y - 4 - size - (line - offset) * (size + 3));
                }
                x += widths[column];
            }
            y -= height;
        }

        void signature(String creatorRole, String creatorName, String recipientRole) throws IOException {
            if (y < MARGIN + 110) newPage();
            y -= 16;
            List<TableRow> signature = List.of(
                    new TableRow(new String[]{"NGƯỜI LẬP BÁO CÁO", "NGƯỜI NHẬN BÁO CÁO"}, true),
                    new TableRow(new String[]{creatorRole, recipientRole}, false),
                    new TableRow(new String[]{"(Ký, ghi rõ họ tên)", "(Ký, đóng dấu xác nhận)"}, false),
                    new TableRow(new String[]{creatorName, ""}, false));
            table("XÁC NHẬN BÁO CÁO", new float[]{1, 1}, new String[]{"Người lập", "Người nhận"}, signature, 9);
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = SIZE.getHeight() - MARGIN;
        }

        void finish() throws IOException {
            close();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset(MARGIN, 20);
                    footer.showText("CineViet | Trang " + (i + 1) + "/" + document.getNumberOfPages());
                    footer.endText();
                }
            }
        }

        private void draw(String value, PDType0Font font, float size, float x, float baseline) throws IOException {
            stream.setNonStrokingColor(new Color(15, 23, 42));
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value);
            stream.endText();
        }

        private List<String> wrap(String value, PDType0Font font, float size, float width) throws IOException {
            String text = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
            List<String> lines = new ArrayList<>();
            while (!text.isEmpty()) {
                int end = 0;
                int lastSpace = -1;
                while (end < text.length()) {
                    int next = end + Character.charCount(text.codePointAt(end));
                    if (font.getStringWidth(text.substring(0, next)) / 1000 * size > width && end > 0) break;
                    if (text.charAt(end) == ' ') lastSpace = end;
                    end = next;
                }
                if (end < text.length() && lastSpace > 0) end = lastSpace;
                lines.add(text.substring(0, end).stripTrailing());
                text = text.substring(end).stripLeading();
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        @Override public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
