package com.cinema.shared.db;

/**
 * Ngoại lệ tầng truy cập dữ liệu, bao bọc lỗi JDBC kèm thông điệp tiếng Việt.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
