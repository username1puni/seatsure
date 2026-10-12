package com.seatsure.support;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.seatsure.dto.CityRequest;
import com.seatsure.dto.MovieRequest;
import com.seatsure.dto.ScreenRequest;
import com.seatsure.dto.ShowRequest;
import com.seatsure.dto.TheatreRequest;
import com.seatsure.entity.MovieKind;
import com.seatsure.entity.Role;
import com.seatsure.entity.User;
import com.seatsure.repository.UserRepository;
import com.seatsure.service.admin.AdminCatalogService;
import com.seatsure.service.admin.ShowAdminService;

@TestComponent
public class TestData {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AdminCatalogService catalog;
    @Autowired
    private ShowAdminService shows;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    public void clean() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("payments", "booking_seats", "show_seats", "bookings", "shows", "seats",
                "screens", "theatres", "cities", "movies")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.update("DELETE FROM users WHERE role <> 'ADMIN'");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    public Long user(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 6) + "@test.local";
        return users.save(new User(name, email, passwordEncoder.encode("password123"), Role.USER)).getId();
    }

    /** A show in a 5 x 10 screen (rows A-E, row E premium) starting the given number of hours from now. */
    public ShowFixture show(int hoursFromNow) {
        Long cityId = catalog.createCity(new CityRequest("Testpur " + UUID.randomUUID().toString().substring(0, 5), "Test State")).id();
        Long theatreId = catalog.createTheatre(new TheatreRequest(cityId, "Test Cinema", "1 Test Road")).id();
        Long screenId = catalog.createScreen(new ScreenRequest(theatreId, "Screen 1", 5, 10, List.of("E"))).id();
        Long movieId = catalog.createMovie(new MovieRequest("Test Movie", "Just for tests", MovieKind.MOVIE, "English",
                "Drama", 120, "U", null, null, true)).id();
        Long showId = shows.create(new ShowRequest(movieId, screenId, LocalDateTime.now(clock).plusHours(hoursFromNow).plusMinutes(1),
                new BigDecimal("200"), new BigDecimal("350"))).id();
        List<Long> seatIds = jdbc.queryForList(
                "select s.id from seats s where s.screen_id = ? order by s.row_label, s.seat_number", Long.class, screenId);
        return new ShowFixture(cityId, theatreId, screenId, movieId, showId, seatIds);
    }

    public int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    public void expireHold(Long bookingId) {
        // timestamps are stored in UTC
        jdbc.update("update bookings set hold_expires_at = utc_timestamp(6) - interval 1 minute where id = ?", bookingId);
    }

    public String ticketQr(Long bookingId) {
        return jdbc.queryForObject("select concat('SEATSURE:', booking_code, ':', ticket_token) from bookings where id = ?",
                String.class, bookingId);
    }

    public record ShowFixture(Long cityId, Long theatreId, Long screenId, Long movieId, Long showId, List<Long> seatIds) {

        /** Seat ids of one row, row 0 is A. */
        public List<Long> row(int row) {
            return seatIds.subList(row * 10, row * 10 + 10);
        }
    }
}
