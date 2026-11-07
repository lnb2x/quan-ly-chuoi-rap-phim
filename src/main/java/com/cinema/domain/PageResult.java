package com.cinema.domain;

import java.util.List;

/**
 * Kết quả phân trang tường minh cho các danh sách nghiệp vụ (loại bỏ cắt ngầm LIMIT 1000).
 */
public record PageResult<T>(
        List<T> items,
        int totalCount,
        int pageNumber,
        int pageSize
) {
    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
        pageSize = Math.max(1, pageSize);
        pageNumber = Math.max(1, pageNumber);
        totalCount = Math.max(0, totalCount);
    }

    public int totalPages() {
        return totalCount <= 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
    }

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return pageNumber < totalPages();
    }

    public int fromIndex() {
        return totalCount == 0 || items.isEmpty() ? 0 : (pageNumber - 1) * pageSize + 1;
    }

    public int toIndex() {
        return totalCount == 0 || items.isEmpty()
                ? 0
                : Math.min(totalCount, (pageNumber - 1) * pageSize + items.size());
    }
}
