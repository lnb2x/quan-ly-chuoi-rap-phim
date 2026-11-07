package com.cinema.feature.screening;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Movie;
import com.cinema.domain.PageResult;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.ScreeningRoom;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.shared.validation.ValidationResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tầng dịch vụ nghiệp vụ cho Quản lý Suất chiếu (Screen 1 - R5).
 * Kiểm tra đầy đủ các quy tắc BR-01, BR-04, BR-05, BR-06 và bảo vệ
 * chống trùng lịch dưới các yêu cầu đồng thời bằng khóa dòng phòng chiếu (FOR UPDATE).
 */
public final class ScreeningService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ScreeningRepository repository;
    private final TransactionTemplate transactionTemplate;

    public ScreeningService(ScreeningRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    public List<CinemaBranch> listBranches() {
        return repository.findAllBranches();
    }

    public List<ScreeningRoom> listRoomsByBranch(Integer branchId) {
        return repository.findRoomsByBranch(branchId);
    }

    public List<Movie> listMovies() {
        return repository.findAllMovies();
    }

    public List<ScreeningDetailView> listScreenings(Integer branchId, Integer roomId, Integer movieId, LocalDate dateFilter) {
        return repository.findScreenings(branchId, roomId, movieId, dateFilter);
    }

    public PageResult<ScreeningDetailView> listScreeningsPage(
            Integer branchId,
            Integer roomId,
            Integer movieId,
            LocalDate dateFilter,
            int pageNumber,
            int pageSize
    ) {
        int safePageSize = Math.max(1, pageSize);
        int totalCount = repository.countScreenings(branchId, roomId, movieId, dateFilter);
        int totalPages = totalCount <= 0 ? 1 : (int) Math.ceil((double) totalCount / safePageSize);
        int effectivePage = Math.min(Math.max(1, pageNumber), totalPages);
        int offset = (effectivePage - 1) * safePageSize;
        List<ScreeningDetailView> items = totalCount == 0
                ? List.of()
                : repository.findScreeningsPage(branchId, roomId, movieId, dateFilter, safePageSize, offset);
        return new PageResult<>(items, totalCount, effectivePage, safePageSize);
    }

    /**
     * Kiểm tra hợp lệ đầu vào của suất chiếu (có thể kiểm thử độc lập ở Unit Test).
     */
    public ValidationResult validateScreeningInput(
            Movie movie,
            ScreeningRoom room,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            String status
    ) {
        ValidationResult vr = new ValidationResult();
        if (movie == null) {
            vr.addError("Vui lòng chọn bộ phim trình chiếu.");
        }
        if (room == null) {
            vr.addError("Vui lòng chọn phòng chiếu.");
        } else if (!room.active()) {
            vr.addError("Phòng chiếu được chọn đang tạm ngưng hoạt động [BR-01].");
        }
        if (startTime == null) {
            vr.addError("Vui lòng nhập thời điểm bắt đầu suất chiếu hợp lệ.");
        }
        if (endTime == null) {
            vr.addError("Vui lòng nhập thời điểm kết thúc suất chiếu hợp lệ.");
        }
        if (startTime != null && endTime != null) {
            if (!endTime.isAfter(startTime)) {
                vr.addError("Thời điểm kết thúc phải lớn hơn thời điểm bắt đầu [BR-05].");
            } else if (movie != null) {
                long minutes = Duration.between(startTime, endTime).toMinutes();
                if (minutes < movie.durationMinutes()) {
                    vr.addError(String.format(
                            "Khoảng thời gian suất chiếu (%d phút) nhỏ hơn thời lượng bộ phim '%s' (%d phút) [BR-05].",
                            minutes, movie.title(), movie.durationMinutes()));
                }
            }
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            vr.addError("Giá vé cơ sở của suất chiếu phải lớn hơn 0 VNĐ [BR-04].");
        }
        if (status == null || (!"OPEN".equals(status) && !"CANCELLED".equals(status))) {
            vr.addError("Trạng thái suất chiếu chỉ nhận giá trị OPEN hoặc CANCELLED.");
        }
        return vr;
    }

    /**
     * Tạo mới suất chiếu an toàn giao dịch: khóa dòng phòng chiếu (FOR UPDATE),
     * kiểm tra thời lượng và xung đột lịch chiếu trước khi chèn.
     */
    public int createScreening(
            int movieId,
            int roomId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice
    ) {
        return transactionTemplate.execute(conn -> {
            repository.lockRoomAndCheckActive(conn, roomId);
            Movie movie = repository.findMovieById(conn, movieId)
                    .orElseThrow(() -> new BusinessRuleException("Không tìm thấy bộ phim có mã #" + movieId));

            ScreeningRoom dummyRoom = new ScreeningRoom(roomId, 0, "", "", "2D", true);
            validateScreeningInput(movie, dummyRoom, startTime, endTime, basePrice, "OPEN").throwIfInvalid();

            List<ScreeningDetailView> overlaps = repository.findOverlappingScreenings(
                    conn, roomId, startTime, endTime, null);
            if (!overlaps.isEmpty()) {
                ScreeningDetailView first = overlaps.getFirst();
                throw new BusinessRuleException(String.format(
                        "Lỗi nghiệp vụ [BR-06]: Trùng lịch chiếu trong %s với suất #%d (%s: %s - %s).",
                        first.roomName(),
                        first.screeningId(),
                        first.movieTitle(),
                        DT_FMT.format(first.startTime()),
                        DT_FMT.format(first.endTime())
                ));
            }

            return repository.insertScreening(conn, movieId, roomId, startTime, endTime, basePrice, "OPEN");
        });
    }

    /**
     * Cập nhật suất chiếu hiện có an toàn giao dịch.
     */
    public void updateScreening(
            int screeningId,
            int movieId,
            int roomId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            String status
    ) {
        transactionTemplate.execute(conn -> {
            int oldRoomId = repository.lockScreeningAndGetRoomId(conn, screeningId);
            repository.lockRoomAndCheckActive(conn, roomId);
            if (oldRoomId != roomId && repository.countTicketsForScreening(conn, screeningId, false) > 0) {
                throw new BusinessRuleException(
                        "Lỗi nghiệp vụ [BR-09]: Không thể đổi phòng chiếu cho suất chiếu #" + screeningId
                                + " vì suất chiếu này đã phát sinh vé.");
            }

            if ("CANCELLED".equals(status) && repository.countTicketsForScreening(conn, screeningId, true) > 0) {
                throw new BusinessRuleException(
                        "Lỗi nghiệp vụ [BR-08]: Không thể hủy suất chiếu #" + screeningId
                                + " khi vẫn còn vé hợp lệ chưa hoàn hủy.");
            }

            Movie movie = repository.findMovieById(conn, movieId)
                    .orElseThrow(() -> new BusinessRuleException("Không tìm thấy bộ phim có mã #" + movieId));

            ScreeningRoom dummyRoom = new ScreeningRoom(roomId, 0, "", "", "2D", true);
            validateScreeningInput(movie, dummyRoom, startTime, endTime, basePrice, status).throwIfInvalid();

            if ("OPEN".equals(status)) {
                List<ScreeningDetailView> overlaps = repository.findOverlappingScreenings(
                        conn, roomId, startTime, endTime, screeningId);
                if (!overlaps.isEmpty()) {
                    ScreeningDetailView first = overlaps.getFirst();
                    throw new BusinessRuleException(String.format(
                            "Lỗi nghiệp vụ [BR-06]: Trùng lịch chiếu trong %s với suất #%d (%s: %s - %s).",
                            first.roomName(),
                            first.screeningId(),
                            first.movieTitle(),
                            DT_FMT.format(first.startTime()),
                            DT_FMT.format(first.endTime())
                    ));
                }
            }

            repository.updateScreening(conn, screeningId, movieId, roomId, startTime, endTime, basePrice, status);
            return null;
        });
    }
}
