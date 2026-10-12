package com.seatsure.service.admin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatsure.dto.CityRequest;
import com.seatsure.dto.CityResponse;
import com.seatsure.dto.MovieRequest;
import com.seatsure.dto.MovieResponse;
import com.seatsure.dto.ScreenRequest;
import com.seatsure.dto.ScreenResponse;
import com.seatsure.dto.TheatreRequest;
import com.seatsure.dto.TheatreResponse;
import com.seatsure.entity.City;
import com.seatsure.entity.Movie;
import com.seatsure.entity.Screen;
import com.seatsure.entity.Seat;
import com.seatsure.entity.SeatCategory;
import com.seatsure.entity.Theatre;
import com.seatsure.exception.BadRequestException;
import com.seatsure.exception.ConflictException;
import com.seatsure.exception.NotFoundException;
import com.seatsure.repository.CityRepository;
import com.seatsure.repository.MovieRepository;
import com.seatsure.repository.ScreenRepository;
import com.seatsure.repository.SeatRepository;
import com.seatsure.repository.ShowRepository;
import com.seatsure.repository.TheatreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private final CityRepository cityRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;

    // ---- cities

    @Transactional
    public CityResponse createCity(CityRequest request) {
        if (cityRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("City already exists");
        }
        return CityResponse.from(cityRepository.save(new City(request.name().trim(), request.state().trim())));
    }

    // ---- theatres

    @Transactional(readOnly = true)
    public List<TheatreResponse> theatres(Long cityId) {
        List<Theatre> theatres = cityId == null ? theatreRepository.findAllByOrderByNameAsc()
                : theatreRepository.findByCityIdOrderByNameAsc(cityId);
        return theatres.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TheatreResponse createTheatre(TheatreRequest request) {
        City city = cityRepository.findById(request.cityId()).orElseThrow(() -> new NotFoundException("City not found"));
        Theatre theatre = theatreRepository.save(new Theatre(city, request.name().trim(), request.address().trim()));
        return toResponse(theatre);
    }

    @Transactional
    public TheatreResponse updateTheatre(Long id, TheatreRequest request) {
        Theatre theatre = theatreRepository.findById(id).orElseThrow(() -> new NotFoundException("Theatre not found"));
        City city = cityRepository.findById(request.cityId()).orElseThrow(() -> new NotFoundException("City not found"));
        theatre.setCity(city);
        theatre.setName(request.name().trim());
        theatre.setAddress(request.address().trim());
        return toResponse(theatre);
    }

    // ---- screens and seat layouts

    @Transactional(readOnly = true)
    public List<ScreenResponse> screens(Long theatreId) {
        return screenRepository.findByTheatreIdOrderByNameAsc(theatreId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScreenResponse screen(Long id) {
        return toResponse(screenRepository.findById(id).orElseThrow(() -> new NotFoundException("Screen not found")));
    }

    /**
     * Creates a screen and its seats. Rows are A, B, C... from the front. Rows listed in
     * premiumRows are priced as premium; by default the last two rows are premium.
     */
    @Transactional
    public ScreenResponse createScreen(ScreenRequest request) {
        Theatre theatre = theatreRepository.findById(request.theatreId())
                .orElseThrow(() -> new NotFoundException("Theatre not found"));
        if (screenRepository.existsByTheatreIdAndNameIgnoreCase(theatre.getId(), request.name().trim())) {
            throw new ConflictException("This theatre already has a screen called " + request.name().trim());
        }
        Set<String> premium = premiumRows(request.premiumRows(), request.rows());
        Screen screen = screenRepository.save(new Screen(theatre, request.name().trim(), request.rows(), request.seatsPerRow()));
        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < request.rows(); r++) {
            String row = rowLabel(r);
            SeatCategory category = premium.contains(row) ? SeatCategory.PREMIUM : SeatCategory.NORMAL;
            for (int n = 1; n <= request.seatsPerRow(); n++) {
                seats.add(new Seat(screen, row, n, category));
            }
        }
        seatRepository.saveAll(seats);
        return toResponse(screen);
    }

    @Transactional
    public void deleteScreen(Long id) {
        Screen screen = screenRepository.findById(id).orElseThrow(() -> new NotFoundException("Screen not found"));
        if (showRepository.existsByScreenId(id)) {
            throw new ConflictException("This screen has shows. Cancel them instead of deleting the screen.");
        }
        seatRepository.deleteAll(seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(id));
        screenRepository.delete(screen);
    }

    static String rowLabel(int index) {
        return String.valueOf((char) ('A' + index));
    }

    static Set<String> premiumRows(List<String> requested, int rows) {
        Set<String> result = new LinkedHashSet<>();
        if (requested == null) {
            for (int r = Math.max(0, rows - 2); r < rows; r++) {
                result.add(rowLabel(r));
            }
            return result;
        }
        for (String row : requested) {
            String label = row.trim().toUpperCase(Locale.ROOT);
            if (label.length() != 1 || label.charAt(0) < 'A' || label.charAt(0) >= 'A' + rows) {
                throw new BadRequestException("Premium row " + row + " is not in this screen");
            }
            result.add(label);
        }
        return result;
    }

    // ---- movies and events

    @Transactional(readOnly = true)
    public List<MovieResponse> movies() {
        return movieRepository.findAllByOrderByTitleAsc().stream().map(MovieResponse::from).toList();
    }

    @Transactional
    public MovieResponse createMovie(MovieRequest request) {
        Movie movie = new Movie();
        apply(movie, request);
        return MovieResponse.from(movieRepository.save(movie));
    }

    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found"));
        apply(movie, request);
        return MovieResponse.from(movie);
    }

    private static void apply(Movie movie, MovieRequest r) {
        movie.setTitle(r.title().trim());
        movie.setDescription(r.description());
        movie.setKind(r.kind());
        movie.setLanguage(r.language().trim());
        movie.setGenre(r.genre().trim());
        movie.setDurationMinutes(r.durationMinutes());
        movie.setCertificate(r.certificate());
        movie.setPosterUrl(r.posterUrl() == null || r.posterUrl().isBlank() ? null : r.posterUrl().trim());
        movie.setReleaseDate(r.releaseDate());
        movie.setActive(r.active() == null || r.active());
    }

    private TheatreResponse toResponse(Theatre t) {
        int screens = screenRepository.findByTheatreIdOrderByNameAsc(t.getId()).size();
        return new TheatreResponse(t.getId(), t.getName(), t.getAddress(), t.getCity().getId(), t.getCity().getName(), screens);
    }

    private ScreenResponse toResponse(Screen s) {
        List<Seat> seats = seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(s.getId());
        List<String> premium = seats.stream().filter(seat -> seat.getCategory() == SeatCategory.PREMIUM)
                .map(Seat::getRowLabel).distinct().toList();
        return new ScreenResponse(s.getId(), s.getTheatre().getId(), s.getTheatre().getName(), s.getName(),
                s.getTotalRows(), s.getSeatsPerRow(), seats.size(), premium);
    }
}
