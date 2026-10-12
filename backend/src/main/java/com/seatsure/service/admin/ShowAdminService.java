package com.seatsure.service.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatsure.dto.AdminShowResponse;
import com.seatsure.dto.PricingRequest;
import com.seatsure.dto.ShowRequest;
import com.seatsure.entity.BookingStatus;
import com.seatsure.entity.Movie;
import com.seatsure.entity.Screen;
import com.seatsure.entity.Show;
import com.seatsure.entity.ShowSeatStatus;
import com.seatsure.entity.ShowStatus;
import com.seatsure.exception.BadRequestException;
import com.seatsure.exception.ConflictException;
import com.seatsure.exception.NotFoundException;
import com.seatsure.repository.BookingRepository;
import com.seatsure.repository.MovieRepository;
import com.seatsure.repository.ScreenRepository;
import com.seatsure.repository.ShowRepository;
import com.seatsure.repository.ShowSeatRepository;
import com.seatsure.service.BookingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowAdminService {

    /** Time between two shows on the same screen for cleaning and ads. */
    static final int CLEANING_MINUTES = 15;

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AdminShowResponse> shows(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        List<Show> shows = showRepository.findBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        Map<Long, Long> available = new HashMap<>();
        if (!shows.isEmpty()) {
            for (Object[] row : showSeatRepository.countAvailable(shows.stream().map(Show::getId).toList())) {
                available.put((Long) row[0], (Long) row[1]);
            }
        }
        return shows.stream().map(s -> toResponse(s, available.getOrDefault(s.getId(), 0L))).toList();
    }

    @Transactional
    public AdminShowResponse create(ShowRequest request) {
        Movie movie = movieRepository.findById(request.movieId()).orElseThrow(() -> new NotFoundException("Movie not found"));
        Screen screen = screenRepository.findById(request.screenId()).orElseThrow(() -> new NotFoundException("Screen not found"));
        if (!movie.isActive()) {
            throw new BadRequestException("This movie is not active");
        }
        if (!request.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new BadRequestException("Show time must be in the future");
        }
        if (request.pricePremium().compareTo(request.priceNormal()) < 0) {
            throw new BadRequestException("Premium price can't be lower than the normal price");
        }
        LocalDateTime start = request.startTime().withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(movie.getDurationMinutes());
        if (showRepository.overlaps(screen.getId(), start.minusMinutes(CLEANING_MINUTES), end.plusMinutes(CLEANING_MINUTES))) {
            throw new ConflictException("Another show is running on " + screen.getName() + " at that time");
        }
        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartTime(start);
        show.setEndTime(end);
        show.setPriceNormal(request.priceNormal());
        show.setPricePremium(request.pricePremium());
        show.setStatus(ShowStatus.SCHEDULED);
        showRepository.saveAndFlush(show);
        int seats = showRepository.createSeatInventory(show.getId(), screen.getId());
        if (seats == 0) {
            throw new BadRequestException("This screen has no seats");
        }
        return toResponse(show, seats);
    }

    /**
     * New prices only apply to bookings made from now on. Each booked seat keeps the price it was
     * sold at (booking_seats.price). The version check stops two admins overwriting each other.
     */
    @Transactional
    public AdminShowResponse updatePricing(Long showId, PricingRequest request) {
        Show show = showRepository.findById(showId).orElseThrow(() -> new NotFoundException("Show not found"));
        if (show.getVersion() != request.version()) {
            throw new ConflictException("Someone else changed this show. Reload and try again.");
        }
        if (request.pricePremium().compareTo(request.priceNormal()) < 0) {
            throw new BadRequestException("Premium price can't be lower than the normal price");
        }
        show.setPriceNormal(request.priceNormal());
        show.setPricePremium(request.pricePremium());
        showRepository.saveAndFlush(show);
        return toResponse(show, showSeatRepository.countByShowIdAndStatus(showId, ShowSeatStatus.AVAILABLE));
    }

    /** Cancels a show and refunds every booking in full. Each booking is handled in its own transaction. */
    public int cancel(Long showId) {
        tx.executeWithoutResult(status -> {
            Show show = showRepository.findById(showId).orElseThrow(() -> new NotFoundException("Show not found"));
            show.setStatus(ShowStatus.CANCELLED);
        });
        List<Long> bookingIds = bookingRepository.findIdsByShowAndStatus(showId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT));
        for (Long id : bookingIds) {
            bookingService.cancelForCancelledShow(id);
        }
        log.info("Show {} cancelled, {} booking(s) refunded or released", showId, bookingIds.size());
        return bookingIds.size();
    }

    private AdminShowResponse toResponse(Show s, long available) {
        long capacity = (long) s.getScreen().getTotalRows() * s.getScreen().getSeatsPerRow();
        return new AdminShowResponse(s.getId(), s.getMovie().getId(), s.getMovie().getTitle(),
                s.getScreen().getTheatre().getId(), s.getScreen().getTheatre().getName(), s.getScreen().getId(),
                s.getScreen().getName(), s.getStartTime(), s.getEndTime(), s.getPriceNormal(), s.getPricePremium(),
                s.getStatus(), capacity - available, capacity, s.getVersion());
    }
}
