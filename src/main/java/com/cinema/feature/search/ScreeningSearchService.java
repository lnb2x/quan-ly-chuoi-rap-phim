package com.cinema.feature.search;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Movie;
import com.cinema.domain.PageResult;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.SearchPageResult;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.shared.validation.BusinessRuleException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tầng dịch vụ nghiệp vụ cho Tra cứu Suất chiếu (Screen 3 - R6).
 */
public final class ScreeningSearchService {

    private final ScreeningSearchRepository searchRepository;
    private final ScreeningRepository screeningRepository;

    public ScreeningSearchService(ScreeningSearchRepository searchRepository, ScreeningRepository screeningRepository) {
        this.searchRepository = searchRepository;
        this.screeningRepository = screeningRepository;
    }

    public List<CinemaBranch> listBranches() {
        return screeningRepository.findAllBranches();
    }

    public List<Movie> listMovies() {
        return screeningRepository.findAllMovies();
    }

    public List<ScreeningDetailView> search(
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats
    ) {
        validateTimeRange(fromTime, toTime);
        return searchRepository.searchScreenings(
                movieKeyword,
                movieId,
                branchId,
                screeningDate,
                fromTime,
                toTime,
                onlyOpenWithAvailableSeats
        );
    }

    public SearchPageResult searchPage(
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats,
            int pageNumber,
            int pageSize
    ) {
        validateTimeRange(fromTime, toTime);
        int safePageSize = Math.max(1, pageSize);
        ScreeningSearchRepository.SearchSummary summary = searchRepository.summarizeSearch(
                movieKeyword, movieId, branchId, screeningDate, fromTime, toTime, onlyOpenWithAvailableSeats);
        int totalCount = summary.totalCount();
        int totalPages = totalCount <= 0 ? 1 : (int) Math.ceil((double) totalCount / safePageSize);
        int effectivePage = Math.min(Math.max(1, pageNumber), totalPages);
        int offset = (effectivePage - 1) * safePageSize;

        List<ScreeningDetailView> items = totalCount == 0
                ? List.of()
                : searchRepository.searchScreeningsPage(
                        movieKeyword,
                        movieId,
                        branchId,
                        screeningDate,
                        fromTime,
                        toTime,
                        onlyOpenWithAvailableSeats,
                        safePageSize,
                        offset
                );
        return new SearchPageResult(
                new PageResult<>(items, totalCount, effectivePage, safePageSize),
                summary.totalAvailableSeats(),
                summary.totalSoldSeats()
        );
    }

    private static void validateTimeRange(LocalTime fromTime, LocalTime toTime) {
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw new BusinessRuleException("Khung giờ kết thúc tìm kiếm không được nhỏ hơn khung giờ bắt đầu.");
        }
    }
}
