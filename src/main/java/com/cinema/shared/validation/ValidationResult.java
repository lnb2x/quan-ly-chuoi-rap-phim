package com.cinema.shared.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kết quả kiểm tra hợp lệ dữ liệu đầu vào với danh sách thông báo lỗi tiếng Việt.
 */
public final class ValidationResult {

    private final List<String> errors = new ArrayList<>();

    public void addError(String message) {
        if (message != null && !message.isBlank()) {
            errors.add(message.trim());
        }
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public String summaryMessage() {
        return String.join("\n", errors);
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            throw new BusinessRuleException(summaryMessage());
        }
    }
}
