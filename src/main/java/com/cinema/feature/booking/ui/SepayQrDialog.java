package com.cinema.feature.booking.ui;

import com.cinema.feature.payment.SandboxPaymentService;
import com.cinema.shared.ui.Theme;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hộp thoại không chặn (modeless) hiển thị mã VietQR động của SePay Sandbox ngay trong giao diện đặt vé.
 * Tự động tải ảnh QR bất đồng bộ (có chế độ dự phòng ngoại tuyến) và kiểm tra trạng thái đơn định kỳ.
 */
public final class SepayQrDialog extends JDialog {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int QR_BOX_SIZE = 220;
    private static final int MAX_QR_DIMENSION = 1_024;

    private final SandboxPaymentService sandboxPayments;
    private final SandboxPaymentService.Checkout checkout;
    private final Consumer<SandboxPaymentService.CheckoutStatus> onTerminalState;
    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);
    private boolean terminalNotified;
    private boolean disposed;

    private final JLabel lblQrImage = new JLabel("Đang tải ảnh VietQR...", SwingConstants.CENTER);
    private final JLabel lblStatus = new JLabel("Đang chờ Webhook SePay xác nhận (PENDING)...");
    private final Timer pollTimer;

    public SepayQrDialog(
            Window owner,
            SandboxPaymentService sandboxPayments,
            SandboxPaymentService.Checkout checkout,
            Consumer<SandboxPaymentService.CheckoutStatus> onTerminalState
    ) {
        super(owner, "Thanh toán VietQR - SePay Sandbox (Đơn #" + checkout.bookingId() + ")", ModalityType.MODELESS);
        this.sandboxPayments = sandboxPayments;
        this.checkout = checkout;
        this.onTerminalState = onTerminalState;

        setName("sepayQrDialog");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        pack();
        setMinimumSize(new Dimension(560, 390));
        setLocationRelativeTo(owner);

        this.pollTimer = new Timer(2_000, e -> refreshStatusAsync());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                disposed = true;
                pollTimer.stop();
            }
        });

        loadQrImageAsync();
        pollTimer.start();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(14, 12));
        root.setBackground(Theme.BG_CARD);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("QUÉT MÃ VIETQR HOẶC MÔ PHỎNG GIAO DỊCH SEPAY SANDBOX");
        title.setFont(Theme.FONT_SUBTITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        root.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(16, 10));
        center.setOpaque(false);

        lblQrImage.setName("lblSepayQrImage");
        lblQrImage.setPreferredSize(new Dimension(QR_BOX_SIZE, QR_BOX_SIZE));
        lblQrImage.setFont(Theme.FONT_SMALL);
        lblQrImage.setOpaque(true);
        lblQrImage.setBackground(new Color(248, 250, 252));
        lblQrImage.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SUBTLE, 1, true));
        center.add(lblQrImage, BorderLayout.WEST);
        center.add(buildDetailsPanel(), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildDetailsPanel() {
        JPanel details = new JPanel(new GridBagLayout());
        details.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 2, 4, 2);

        int row = 0;
        addInfoRow(details, gbc, row++, "Ngân hàng thụ hưởng:", checkout.bankCode(), "lblSepayBank");
        addInfoRow(details, gbc, row++, "Số tài khoản nhận:",
                checkout.accountNumber() + " (" + checkout.accountName() + ")", "lblSepayAccount");
        addInfoRow(details, gbc, row++, "Số tiền chính xác:",
                Theme.formatCurrency(checkout.amount()), "lblSepayAmount");

        gbc.gridy = row++;
        details.add(buildTransferCodeSection(), gbc);

        addInfoRow(details, gbc, row++, "Hạn giữ ghế (15 phút):",
                checkout.expiresAt().format(TIME_FMT), "lblSepayExpiresAt");

        lblStatus.setName("lblSepayStatus");
        lblStatus.putClientProperty("html.disable", Boolean.TRUE);
        lblStatus.setFont(Theme.FONT_BOLD);
        lblStatus.setForeground(new Color(180, 83, 9));
        gbc.gridy = row;
        details.add(lblStatus, gbc);
        return details;
    }

    private JPanel buildTransferCodeSection() {
        JPanel codeRow = new JPanel(new BorderLayout(6, 0));
        codeRow.setOpaque(false);
        JLabel lblCode = new JLabel(checkout.transferCode());
        lblCode.setName("lblSepayTransferCode");
        lblCode.putClientProperty("html.disable", Boolean.TRUE);
        lblCode.setFont(Theme.FONT_KPI_VALUE);
        lblCode.setForeground(Theme.ACCENT_PRIMARY);
        JButton btnCopy = Theme.createSecondaryButton("Sao chép mã CK");
        btnCopy.setName("btnCopySepayCode");
        btnCopy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(checkout.transferCode()), null);
            btnCopy.setText("Đã chép!");
        });
        codeRow.add(lblCode, BorderLayout.CENTER);
        codeRow.add(btnCopy, BorderLayout.EAST);

        JPanel codeWrap = new JPanel(new BorderLayout(2, 2));
        codeWrap.setOpaque(false);
        JLabel codeTitle = new JLabel("Nội dung chuyển khoản bắt buộc (*):");
        codeTitle.setFont(Theme.FONT_BOLD);
        codeWrap.add(codeTitle, BorderLayout.NORTH);
        codeWrap.add(codeRow, BorderLayout.CENTER);
        return codeWrap;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);

        JButton btnRefresh = Theme.createSecondaryButton("Kiểm tra trạng thái");
        btnRefresh.setName("btnRefreshSepayStatus");
        btnRefresh.addActionListener(e -> refreshStatusAsync());

        JButton btnClose = Theme.createPrimaryButton("Đóng");
        btnClose.setName("btnCloseSepayDialog");
        btnClose.addActionListener(e -> dispose());

        footer.add(btnRefresh);
        footer.add(btnClose);
        return footer;
    }

    private static void addInfoRow(JPanel parent, GridBagConstraints gbc, int row,
                                   String caption, String value, String componentName) {
        JPanel wrap = new JPanel(new BorderLayout(2, 1));
        wrap.setOpaque(false);
        JLabel lblCap = new JLabel(caption);
        lblCap.setFont(Theme.FONT_SMALL);
        lblCap.setForeground(Theme.TEXT_MUTED);
        JLabel lblVal = new JLabel(value);
        lblVal.setName(componentName);
        lblVal.putClientProperty("html.disable", Boolean.TRUE);
        lblVal.setFont(Theme.FONT_BOLD);
        lblVal.setForeground(Theme.TEXT_PRIMARY);
        wrap.add(lblCap, BorderLayout.NORTH);
        wrap.add(lblVal, BorderLayout.CENTER);
        gbc.gridy = row;
        parent.add(wrap, gbc);
    }

    private void loadQrImageAsync() {
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                byte[] bytes = sandboxPayments.fetchQrImage(checkout.qrImageUri());
                BufferedImage img = decodeBoundedQrImage(bytes);
                Image scaled = img.getScaledInstance(QR_BOX_SIZE - 12, QR_BOX_SIZE - 12, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }

            @Override
            protected void done() {
                if (disposed || !isDisplayable()) {
                    return;
                }
                try {
                    lblQrImage.setText("");
                    lblQrImage.setIcon(get());
                } catch (Exception ex) {
                    lblQrImage.setIcon(null);
                    lblQrImage.setText("<html><div style='text-align:center;padding:8px;'>"
                            + "<b>VietQR Sandbox</b><br/>Không tải được ảnh trực tuyến.<br/>"
                            + "Dùng mã <b>" + escapeHtml(checkout.transferCode()) + "</b><br/>"
                            + "trong mục Mô phỏng giao dịch SePay.</div></html>");
                }
            }
        }.execute();
    }

    static BufferedImage decodeBoundedQrImage(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                throw new IOException("Không tạo được luồng đọc ảnh QR.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("Không nhận diện được định dạng ảnh VietQR.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_QR_DIMENSION || h > MAX_QR_DIMENSION) {
                    throw new IOException("Kích thước điểm ảnh VietQR vượt giới hạn an toàn.");
                }
                BufferedImage img = reader.read(0);
                if (img == null) {
                    throw new IOException("Không giải mã được ảnh VietQR.");
                }
                return img;
            } finally {
                reader.dispose();
            }
        }
    }

    private static String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public void refreshStatusAsync() {
        if (disposed || !pollInFlight.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<Optional<SandboxPaymentService.CheckoutStatus>, Void>() {
            @Override
            protected Optional<SandboxPaymentService.CheckoutStatus> doInBackground() {
                return sandboxPayments.statusOf(checkout.bookingId());
            }

            @Override
            protected void done() {
                pollInFlight.set(false);
                if (disposed || !isDisplayable()) {
                    return;
                }
                try {
                    Optional<SandboxPaymentService.CheckoutStatus> opt = get();
                    opt.ifPresent(SepayQrDialog.this::applyStatusOnEdt);
                } catch (Exception ignored) {
                    // Keep polling quietly if transient DB read contention occurs
                }
            }
        }.execute();
    }

    private void applyStatusOnEdt(SandboxPaymentService.CheckoutStatus st) {
        if ("CONFIRMED".equals(st.bookingStatus()) && "SUCCESS".equals(st.paymentStatus())) {
            pollTimer.stop();
            lblStatus.setForeground(new Color(21, 128, 61));
            lblStatus.setText("Đã xác nhận thanh toán thành công! (" + st.transactionRef() + ")");
            notifyTerminalOnce(st);
        } else if ("CANCELLED".equals(st.bookingStatus())) {
            pollTimer.stop();
            lblStatus.setForeground(new Color(185, 28, 28));
            lblStatus.setText("Đơn đã hết hạn hoặc bị hủy (" + st.bookingStatus() + ").");
            notifyTerminalOnce(st);
        }
    }

    private void notifyTerminalOnce(SandboxPaymentService.CheckoutStatus st) {
        if (!terminalNotified) {
            terminalNotified = true;
            if (onTerminalState != null) {
                onTerminalState.accept(st);
            }
        }
    }
}
