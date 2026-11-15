package com.cinema.unit;

import com.cinema.shared.ui.UiTask;
import com.cinema.shared.validation.BusinessRuleException;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class UiTaskTest {
    @Test
    void workerRunsOutsideEdtAndEdtCanProcessEventsBeforeItFinishes() throws Exception {
        AtomicBoolean eventProcessed = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            int result = UiTask.call(new JPanel(), "Đang kiểm tra tác vụ nền...", () -> {
                assertFalse(SwingUtilities.isEventDispatchThread());
                CountDownLatch rendered = new CountDownLatch(1);
                SwingUtilities.invokeLater(() -> {
                    eventProcessed.set(true);
                    rendered.countDown();
                });
                try {
                    assertTrue(rendered.await(5, TimeUnit.SECONDS), "EDT phải tiếp tục xử lý sự kiện khi tác vụ đang chạy");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
                return 42;
            });
            assertEquals(42, result);
        });
        assertTrue(eventProcessed.get());
    }

    @Test
    void businessFailureAndErrorKeepTheirOriginalType() throws Exception {
        BusinessRuleException failure = new BusinessRuleException("Ghế đã được đặt.");
        AssertionError error = new AssertionError("Không được che lỗi tác vụ.");
        SwingUtilities.invokeAndWait(() -> {
            assertSame(failure, assertThrows(BusinessRuleException.class,
                    () -> UiTask.call(new JPanel(), "Đang kiểm tra lỗi...", () -> { throw failure; })));
            assertSame(error, assertThrows(AssertionError.class,
                    () -> UiTask.call(new JPanel(), "Đang kiểm tra lỗi...", () -> { throw error; })));
        });
    }
}
