package com.seatsure.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatsure.dto.CityResponse;
import com.seatsure.dto.MovieResponse;
import com.seatsure.dto.SeatMapResponse;
import com.seatsure.dto.SeatRow;
import com.seatsure.dto.SeatView;
import com.seatsure.dto.ShowDetailResponse;
import com.seatsure.dto.ShowSlot;
import com.seatsure.dto.TheatreShowsResponse;
import com.seatsure.entity.MovieKind;
import com.seatsure.entity.Seat;
import com.seatsure.entity.SeatCategory;
import com.seatsure.entity.Show;
import com.seatsure.entity.ShowSeat;
import com.seatsure.entity.Theatre;
import com.seatsure.exception.NotFoundException;
import com.seatsure.repository.CityRepository;
import com.seatsure.repository.MovieRepository;
import com.seatsure.repository.ShowRepository;
import com.seatsure.repository.ShowSeatRepository;

/** Read only side: what is playing where, and the seat map of a show. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CityRepository cityRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final Clock clock;
    private final int holdMinutes;
    private final int maxSeats;

    public CatalogService(CityRepository cityRepository, MovieRepository movieRepository, ShowRepository showRepository,
                          ShowSeatRepository showSeatRepository, Clock clock,
                          @Value("${app.booking.hold-minutes}") int holdMinutes,
                          @Value("${app.booking.max-seats}") int maxSeats) {
        this.cityRepository = cityRepository;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.clock = clock;
        this.holdMinutes = holdMinutes;
        this.maxSeats = maxSeats;
    }

    public List<CityResponse> cities() {
        return cityRepository.findAllByOrderByNameAsc().stream().map(CityResponse::from).toList();
    }

    /** Movies and events with at least one upcoming show in the city on that date. */
    public List<MovieResponse> playing(Long cityId, LocalDate date, MovieKind kind) {
        LocalDateTime[] window = window(date);
        return movieRepository.findPlaying(cityId, window[0], window[1], kind).stream()
                .map(MovieResponse::from).toList();
    }

    public MovieResponse movie(Long id) {
        return movieRepository.findById(id).map(MovieResponse::from)
                .orElseThrow(() -> new NotFoundException("Movie not found"));
    }

    public List<LocalDate> showDates(Long movieId, Long cityId) {
        return showRepository.findShowDates(movieId, cityId, LocalDateTime.now(clock));
    }

    public List<TheatreShowsResponse> showsForMovie(Long movieId, Long cityId, LocalDate date) {
        LocalDateTime[] window = window(date);
        List<Show> shows = showRepository.findForMovie(movieId, cityId, window[0], window[1]);
        if (shows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> available = new HashMap<>();
        for (Object[] row : showSeatRepository.countAvailable(shows.stream().map(Show::getId).toList())) {
            available.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, List<Show>> byTheatre = new LinkedHashMap<>();
        Map<Long, Theatre> theatres = new HashMap<>();
        for (Show show : shows) {
            Theatre theatre = show.getScreen().getTheatre();
            theatres.put(theatre.getId(), theatre);
            byTheatre.computeIfAbsent(theatre.getId(), k -> new ArrayList<>()).add(show);
        }
        List<TheatreShowsResponse> result = new ArrayList<>();
        byTheatre.forEach((theatreId, list) -> {
            Theatre theatre = theatres.get(theatreId);
            List<ShowSlot> slots = list.stream()
                    .sorted(Comparator.comparing(Show::getStartTime))
                    .map(s -> new ShowSlot(s.getId(), s.getStartTime(), s.getScreen().getName(), s.getPriceNormal(),
                            s.getPricePremium(), available.getOrDefault(s.getId(), 0L),
                            (long) s.getScreen().getTotalRows() * s.getScreen().getSeatsPerRow()))
                    .toList();
            result.add(new TheatreShowsResponse(theatreId, theatre.getName(), theatre.getAddress(), slots));
        });
        return result;
    }

    public ShowDetailResponse show(Long showId) {
        Show s = showRepository.findDetailed(showId).orElseThrow(() -> new NotFoundException("Show not found"));
        Theatre t = s.getScreen().getTheatre();
        return new ShowDetailResponse(s.getId(), MovieResponse.from(s.getMovie()), t.getId(), t.getName(), t.getAddress(),
                t.getCity().getName(), s.getScreen().getName(), s.getStartTime(), s.getEndTime(), s.getPriceNormal(),
                s.getPricePremium(), s.getStatus());
    }

    public SeatMapResponse seatMap(Long showId) {
        Show show = showRepository.findById(showId).orElseThrow(() -> new NotFoundException("Show not found"));
        // TreeMap keyed so that row Z comes before AA if a hall ever gets that big
        Map<String, List<ShowSeat>> rows = new TreeMap<>(Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder()));
        for (ShowSeat ss : showSeatRepository.findByShowId(showId)) {
            rows.computeIfAbsent(ss.getSeat().getRowLabel(), k -> new ArrayList<>()).add(ss);
        }
        List<SeatRow> result = new ArrayList<>();
        rows.forEach((label, seats) -> {
            seats.sort(Comparator.comparingInt(ss -> ss.getSeat().getSeatNumber()));
            SeatCategory category = seats.get(0).getSeat().getCategory();
            List<SeatView> views = seats.stream().map(ss -> {
                Seat seat = ss.getSeat();
                return new SeatView(seat.getId(), seat.getSeatNumber(), seat.getLabel(), seat.getCategory(),
                        ss.getStatus(), show.priceFor(seat.getCategory()));
            }).toList();
            result.add(new SeatRow(label, category, views));
        });
        return new SeatMapResponse(showId, holdMinutes, maxSeats, result);
    }

    private LocalDateTime[] window(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate day = date == null ? now.toLocalDate() : date;
        LocalDateTime from = day.atStartOfDay();
        if (from.isBefore(now)) {
            from = now; // shows that already started can't be booked
        }
        LocalDateTime to = date == null ? now.plusDays(30) : day.plusDays(1).atStartOfDay();
        return new LocalDateTime[] {from, to};
    }
}
