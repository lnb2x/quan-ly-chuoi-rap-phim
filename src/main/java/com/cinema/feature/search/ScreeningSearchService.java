package com.cinema.feature.search;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Movie;
import com.cinema.domain.ScreeningDetailView;
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
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw new BusinessRuleException("Khung giờ kết thúc tìm kiếm không được nhỏ hơn khung giờ bắt đầu.");
        }
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
}
