package com.cinema.shared.ui;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** Keeps the synchronous screen API while JDBC and file work run outside the EDT. */
public final class UiTask {
    private UiTask() {}

    public static <T> T call(Component parent, String message, Supplier<T> operation) {
        if (!SwingUtilities.isEventDispatchThread()) {
            return operation.get();
        }

        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog progress = new JDialog(owner, "Đang xử lý", Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        content.add(new JLabel(message), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        content.add(bar, BorderLayout.CENTER);
        progress.setContentPane(content);
        progress.pack();
        progress.setLocationRelativeTo(parent);

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() {
                return operation.get();
            }
            @Override protected void done() {
                progress.dispose();
            }
        };
        worker.execute();
        progress.setVisible(true);
        try {
            return worker.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tác vụ bị gián đoạn. Vui lòng tải lại dữ liệu để kiểm tra kết quả.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Không thể hoàn tất tác vụ.", cause);
        } finally {
            progress.dispose();
        }
    }

    public static void run(Component parent, String message, Runnable operation) {
        call(parent, message, () -> {
            operation.run();
            return null;
        });
    }
}
