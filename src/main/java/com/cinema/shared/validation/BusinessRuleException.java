package com.cinema.shared.validation;

/**
 * Ngoại lệ biểu diễn vi phạm quy tắc nghiệp vụ với thông báo tiếng Việt thân thiện.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
