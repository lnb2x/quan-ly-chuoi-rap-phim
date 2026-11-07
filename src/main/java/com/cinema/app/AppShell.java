package com.cinema.app;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.booking.ui.TicketBookingPanel;
import com.cinema.feature.report.revenue.RevenueReportRepository;
import com.cinema.feature.report.revenue.RevenueReportService;
import com.cinema.feature.report.revenue.ui.RevenueReportPanel;
import com.cinema.feature.report.sales.TicketSalesReportRepository;
import com.cinema.feature.report.sales.TicketSalesReportService;
import com.cinema.feature.report.sales.ui.TicketSalesReportPanel;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.feature.screening.ui.ScreeningManagementPanel;
import com.cinema.feature.search.ScreeningSearchRepository;
import com.cinema.feature.search.ScreeningSearchService;
import com.cinema.feature.search.ui.ScreeningSearchPanel;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cửa sổ chính của Ứng dụng Desktop Hệ thống Quản lý Chuỗi Rạp Chiếu Phim CineViet.
 * Thiết kế theo phong cách hiện đại với thanh điều hướng dọc (Sidebar Navigation)
 * gồm đúng 5 phân hệ chức năng bắt buộc (R5, R6, R7).
 */
public final class AppShell extends JFrame {

    public static final String TAB_SCREENING = "SCREENING_MANAGEMENT";
    public static final String TAB_BOOKING = "TICKET_BOOKING";
    public static final String TAB_SEARCH = "SCREENING_SEARCH";
    public static final String TAB_REVENUE_REPORT = "REVENUE_REPORT";
    public static final String TAB_SALES_REPORT = "TICKET_SALES_REPORT";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();

    private final ScreeningManagementPanel screeningPanel;
    private final TicketBookingPanel bookingPanel;
    private final ScreeningSearchPanel searchPanel;
    private final RevenueReportPanel revenueReportPanel;
    private final TicketSalesReportPanel salesReportPanel;

    public AppShell(AppConfig appConfig) {
        super("Hệ Thống Quản Lý Chuỗi Rạp Chiếu Phim CineViet — Java 21 LTS & MySQL 8.4 LTS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1420, 880);
        setMinimumSize(new Dimension(1240, 760));
        setLocationRelativeTo(null);

        ConnectionProvider connectionProvider = new ConnectionProvider(appConfig);
        connectionProvider.logSafeStartupDiagnostics();
        TransactionTemplate transactionTemplate = new TransactionTemplate(connectionProvider);
        PdfReportWriter pdfReportWriter = new PdfReportWriter();

        ScreeningRepository screeningRepository = new ScreeningRepository(connectionProvider);
        ScreeningService screeningService = new ScreeningService(screeningRepository, transactionTemplate);

        BookingRepository bookingRepository = new BookingRepository(connectionProvider);
        BookingService bookingService = new BookingService(bookingRepository, transactionTemplate);

        ScreeningSearchRepository searchRepository = new ScreeningSearchRepository(connectionProvider);
        ScreeningSearchService searchService = new ScreeningSearchService(searchRepository, screeningRepository);

        RevenueReportRepository revenueRepository = new RevenueReportRepository(connectionProvider);
        RevenueReportService revenueService = new RevenueReportService(revenueRepository, screeningRepository, pdfReportWriter);

        TicketSalesReportRepository salesRepository = new TicketSalesReportRepository(connectionProvider);
        TicketSalesReportService salesService = new TicketSalesReportService(salesRepository, screeningRepository, pdfReportWriter);

        this.screeningPanel = new ScreeningManagementPanel(screeningService);
        this.bookingPanel = new TicketBookingPanel(bookingService);
        this.searchPanel = new ScreeningSearchPanel(searchService);
        this.revenueReportPanel = new RevenueReportPanel(revenueService);
        this.salesReportPanel = new TicketSalesReportPanel(salesService);

        contentPanel.setBackground(Theme.BG_APP);
        contentPanel.add(screeningPanel, TAB_SCREENING);
        contentPanel.add(bookingPanel, TAB_BOOKING);
        contentPanel.add(searchPanel, TAB_SEARCH);
        contentPanel.add(revenueReportPanel, TAB_REVENUE_REPORT);
        contentPanel.add(salesReportPanel, TAB_SALES_REPORT);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(appConfig), BorderLayout.WEST);
        root.add(contentPanel, BorderLayout.CENTER);
        setContentPane(root);

        showScreen(TAB_SCREENING);
    }

    private JPanel buildSidebar(AppConfig config) {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(265, 0));
        sidebar.setBackground(Theme.BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createEmptyBorder(18, 14, 18, 14));

        // Brand Header
        JPanel brandBox = new JPanel(new GridLayout(3, 1, 2, 2));
        brandBox.setOpaque(false);
        brandBox.setBorder(BorderFactory.createEmptyBorder(0, 6, 18, 6));
        JLabel lblBrand = new JLabel("CINEVIET ENTERPRISE");
        lblBrand.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblBrand.setForeground(Color.WHITE);
        JLabel lblSub = new JLabel("Quản lý Chuỗi Rạp Chiếu Phim");
        lblSub.setFont(Theme.FONT_SMALL);
        lblSub.setForeground(new Color(148, 163, 184));
        JLabel lblStandard = new JLabel("Chuẩn 3NF • ACID Transaction");
        lblStandard.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblStandard.setForeground(new Color(56, 189, 248));
        brandBox.add(lblBrand);
        brandBox.add(lblSub);
        brandBox.add(lblStandard);

        // Navigation Items
        JPanel navList = new JPanel(new GridLayout(8, 1, 0, 8));
        navList.setOpaque(false);

        navList.add(createSectionLabel("NHẬP LIỆU NGHIỆP VỤ (R5)"));
        navList.add(createNavButton(TAB_SCREENING, "1. Quản lý Suất chiếu"));
        navList.add(createNavButton(TAB_BOOKING, "2. Đặt vé & Chọn ghế"));

        navList.add(createSectionLabel("TRA CỨU VẬN HÀNH (R6)"));
        navList.add(createNavButton(TAB_SEARCH, "3. Tra cứu Suất chiếu"));

        navList.add(createSectionLabel("BÁO CÁO QUẢN TRỊ & PDF (R7)"));
        navList.add(createNavButton(TAB_REVENUE_REPORT, "4. Báo cáo Doanh thu"));
        navList.add(createNavButton(TAB_SALES_REPORT, "5. Báo cáo Tình hình Bán vé"));

        // Footer DB Connection Status
        JPanel footer = new JPanel(new GridLayout(3, 1, 2, 2));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 6, 0, 6));
        JLabel lblDb = new JLabel("● CSDL: MySQL 8.4 LTS (InnoDB)");
        lblDb.setFont(Theme.FONT_BOLD);
        lblDb.setForeground(new Color(74, 222, 128));
        JLabel lblUser = new JLabel("Tài khoản: " + config.username() + " (Least-Priv)");
        lblUser.setFont(Theme.FONT_SMALL);
        lblUser.setForeground(new Color(148, 163, 184));
        JLabel lblJava = new JLabel("Java Runtime: " + System.getProperty("java.version"));
        lblJava.setFont(Theme.FONT_SMALL);
        lblJava.setForeground(new Color(148, 163, 184));
        footer.add(lblDb);
        footer.add(lblUser);
        footer.add(lblJava);

        JPanel topWrap = new JPanel(new BorderLayout());
        topWrap.setOpaque(false);
        topWrap.add(brandBox, BorderLayout.NORTH);
        topWrap.add(navList, BorderLayout.CENTER);

        sidebar.add(topWrap, BorderLayout.NORTH);
        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    private JLabel createSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(148, 163, 184));
        lbl.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 0));
        return lbl;
    }

    private JButton createNavButton(String key, String text) {
        JButton btn = new JButton(text);
        btn.setName("nav_" + key);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(Theme.FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(Theme.BG_SIDEBAR);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> showScreen(key));
        navButtons.put(key, btn);
        return btn;
    }

    public void showScreen(String screenKey) {
        cardLayout.show(contentPanel, screenKey);
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            if (entry.getKey().equals(screenKey)) {
                entry.getValue().setBackground(Theme.BG_SIDEBAR_ACTIVE);
            } else {
                entry.getValue().setBackground(Theme.BG_SIDEBAR);
            }
        }
        switch (screenKey) {
            case TAB_SCREENING -> screeningPanel.refreshTable();
            case TAB_BOOKING -> bookingPanel.reloadScreeningsDropdown();
            case TAB_SEARCH -> searchPanel.executeSearch();
            case TAB_REVENUE_REPORT -> revenueReportPanel.refreshPreview();
            case TAB_SALES_REPORT -> salesReportPanel.refreshPreview();
            default -> {
            }
        }
    }

    public ScreeningManagementPanel getScreeningPanel() {
        return screeningPanel;
    }

    public TicketBookingPanel getBookingPanel() {
        return bookingPanel;
    }

    public ScreeningSearchPanel getSearchPanel() {
        return searchPanel;
    }

    public RevenueReportPanel getRevenueReportPanel() {
        return revenueReportPanel;
    }

    public TicketSalesReportPanel getSalesReportPanel() {
        return salesReportPanel;
    }
}
