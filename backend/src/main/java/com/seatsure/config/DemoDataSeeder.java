package com.seatsure.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.seatsure.dto.AdminShowResponse;
import com.seatsure.dto.CityRequest;
import com.seatsure.dto.HoldRequest;
import com.seatsure.dto.MockCheckoutRequest;
import com.seatsure.dto.MockCheckoutResponse;
import com.seatsure.dto.MovieRequest;
import com.seatsure.dto.MovieResponse;
import com.seatsure.dto.OrderResponse;
import com.seatsure.dto.ScreenRequest;
import com.seatsure.dto.ScreenResponse;
import com.seatsure.dto.SeatRow;
import com.seatsure.dto.SeatView;
import com.seatsure.dto.ShowRequest;
import com.seatsure.dto.TheatreRequest;
import com.seatsure.dto.TheatreResponse;
import com.seatsure.dto.VerifyPaymentRequest;
import com.seatsure.entity.MovieKind;
import com.seatsure.entity.Role;
import com.seatsure.entity.ShowSeatStatus;
import com.seatsure.entity.User;
import com.seatsure.repository.CityRepository;
import com.seatsure.repository.UserRepository;
import com.seatsure.service.BookingService;
import com.seatsure.service.CatalogService;
import com.seatsure.service.PaymentService;
import com.seatsure.service.admin.AdminCatalogService;
import com.seatsure.service.admin.ShowAdminService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fills an empty database with cities, theatres, movies and a few days of shows so the app can
 * be tried right away. Everything goes through the normal services, including some sample
 * bookings paid with the mock gateway. Turn off with SEED_DEMO_DATA=false.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final int DAYS = 5;
    private static final LocalTime[] SLOTS = {LocalTime.of(10, 0), LocalTime.of(13, 30), LocalTime.of(17, 0), LocalTime.of(20, 30)};

    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final AdminCatalogService catalogAdmin;
    private final ShowAdminService showAdmin;
    private final CatalogService catalogService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${app.seed.demo-data}")
    private boolean enabled;

    private record CitySeed(String name, String state, int basePrice, String[][] theatres) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || cityRepository.count() > 0) {
            return;
        }
        long started = System.currentTimeMillis();
        List<MovieResponse> movies = createMovies();
        List<MovieResponse> films = movies.stream().filter(m -> m.kind() == MovieKind.MOVIE).toList();
        List<MovieResponse> events = movies.stream().filter(m -> m.kind() == MovieKind.EVENT).toList();

        List<CitySeed> cities = List.of(
                new CitySeed("Bengaluru", "Karnataka", 200, new String[][] {
                        {"Lakeview Cinemas", "100 Feet Road, Indiranagar, Bengaluru 560038"},
                        {"Silver Screen Multiplex", "11th Main, 4th Block, Jayanagar, Bengaluru 560011"}}),
                new CitySeed("Mysuru", "Karnataka", 150, new String[][] {
                        {"Palace Talkies", "Sayyaji Rao Road, Mysuru 570001"},
                        {"Chamundi Cineplex", "Hunsur Road, Mysuru 570017"}}),
                new CitySeed("Chennai", "Tamil Nadu", 180, new String[][] {
                        {"Marina Cinemas", "Anna Salai, Chennai 600002"},
                        {"Velachery Screens", "Velachery Main Road, Chennai 600042"}}),
                new CitySeed("Hyderabad", "Telangana", 180, new String[][] {
                        {"Hitech City Multiplex", "Madhapur, Hyderabad 500081"},
                        {"Ameerpet Cinemas", "SR Nagar Main Road, Ameerpet, Hyderabad 500038"}}),
                new CitySeed("Mumbai", "Maharashtra", 250, new String[][] {
                        {"Bandra Picture House", "Hill Road, Bandra West, Mumbai 400050"},
                        {"Andheri Cineplex", "New Link Road, Andheri West, Mumbai 400053"}}));

        List<AdminShowResponse> shows = new ArrayList<>();
        int movieCursor = 0;
        for (CitySeed seed : cities) {
            Long cityId = catalogAdmin.createCity(new CityRequest(seed.name(), seed.state())).id();
            for (String[] t : seed.theatres()) {
                TheatreResponse theatre = catalogAdmin.createTheatre(new TheatreRequest(cityId, t[0], t[1]));
                ScreenResponse big = catalogAdmin.createScreen(new ScreenRequest(theatre.id(), "Screen 1", 10, 14, null));
                ScreenResponse small = catalogAdmin.createScreen(new ScreenRequest(theatre.id(), "Screen 2", 8, 12, null));
                for (int day = 0; day < DAYS; day++) {
                    LocalDate date = LocalDate.now(clock).plusDays(day);
                    for (int slot = 0; slot < SLOTS.length; slot++) {
                        MovieResponse onBig = films.get((movieCursor + slot + day) % films.size());
                        shows.addAll(schedule(onBig, big.id(), date.atTime(SLOTS[slot]), seed.basePrice()));
                        boolean eventNight = slot == SLOTS.length - 1 && !events.isEmpty() && day % 2 == 0;
                        MovieResponse onSmall = eventNight ? events.get((day / 2) % events.size())
                                : films.get((movieCursor + slot + day + 3) % films.size());
                        shows.addAll(schedule(onSmall, small.id(), date.atTime(SLOTS[slot]), seed.basePrice()));
                    }
                }
                movieCursor++;
            }
        }
        int bookings = createSampleBookings(shows);
        log.info("Demo data ready: {} cities, {} movies/events, {} shows, {} sample bookings in {} ms",
                cities.size(), movies.size(), shows.size(), bookings, System.currentTimeMillis() - started);
    }

    private List<AdminShowResponse> schedule(MovieResponse movie, Long screenId, LocalDateTime start, int basePrice) {
        if (!start.isAfter(LocalDateTime.now(clock).plusMinutes(30))) {
            return List.of();
        }
        boolean event = movie.kind() == MovieKind.EVENT;
        int normal = event ? basePrice + 249 : basePrice + (start.getHour() >= 17 ? 50 : 0);
        int premium = normal + (event ? 250 : 150);
        return List.of(showAdmin.create(new ShowRequest(movie.id(), screenId, start,
                BigDecimal.valueOf(normal), BigDecimal.valueOf(premium))));
    }

    private List<MovieResponse> createMovies() {
        LocalDate today = LocalDate.now(clock);
        Object[][] data = {
                {"Kaveri Nights", "A night-shift nurse in Mysuru has one week to save the town's oldest single-screen theatre from being torn down.",
                        MovieKind.MOVIE, "Kannada", "Drama", 142, "UA"},
                {"Monsoon Express", "A Mumbai to Goa train, a missing diamond and eleven passengers who all have something to hide.",
                        MovieKind.MOVIE, "Hindi", "Thriller", 128, "UA"},
                {"The Last Over", "A small-town fast bowler gets one last chance at the national team in a final that goes down to the last ball.",
                        MovieKind.MOVIE, "Hindi", "Sports, Drama", 136, "U"},
                {"Little Astronaut", "Nine year old Meera builds a rocket out of scrap and accidentally lands on a moon full of lost toys.",
                        MovieKind.MOVIE, "English", "Animation, Family", 98, "U"},
                {"Code Red Bengaluru", "A cyber crime officer races against the clock when the city's traffic signals are hacked during rush hour.",
                        MovieKind.MOVIE, "Kannada", "Action", 151, "A"},
                {"Chennai Chronicles", "Three roommates, one rented flat and a landlord who refuses to retire. A comedy about growing up broke.",
                        MovieKind.MOVIE, "Tamil", "Comedy", 124, "UA"},
                {"Stand-up Saturdays", "Two hours of fresh stand-up from the best open mic comics in town. Strictly 18+.",
                        MovieKind.EVENT, "Hindi, English", "Comedy Show", 120, "A"},
                {"Unplugged: Indie Music Night", "Acoustic sets from four independent bands, with a sing-along encore.",
                        MovieKind.EVENT, "Multi-language", "Concert", 150, "U"}};
        List<MovieResponse> result = new ArrayList<>();
        for (Object[] m : data) {
            result.add(catalogAdmin.createMovie(new MovieRequest((String) m[0], (String) m[1], (MovieKind) m[2],
                    (String) m[3], (String) m[4], (Integer) m[5], (String) m[6], null, today.minusDays(7), true)));
        }
        return result;
    }

    /** Books a few seats through the real hold + mock payment flow so the dashboard isn't empty. */
    private int createSampleBookings(List<AdminShowResponse> shows) {
        if (shows.isEmpty()) {
            return 0;
        }
        String[] names = {"Ananya Rao", "Rahul Verma", "Priya Nair", "Kiran Kumar", "Sneha Reddy", "Arjun Mehta"};
        List<Long> userIds = new ArrayList<>();
        for (String name : names) {
            String email = name.toLowerCase().replace(' ', '.') + "@example.com";
            // random password nobody knows, these accounts only exist to own sample bookings
            User user = new User(name, email, passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER);
            userIds.add(userRepository.save(user).getId());
        }
        Random random = new Random(42);
        int created = 0;
        for (int i = 0; i < 60; i++) {
            AdminShowResponse show = shows.get(random.nextInt(Math.min(shows.size(), 160)));
            Long userId = userIds.get(random.nextInt(userIds.size()));
            List<Long> seatIds = pickAdjacentSeats(show.id(), 1 + random.nextInt(4), random);
            if (seatIds.isEmpty()) {
                continue;
            }
            try {
                var booking = bookingService.holdSeats(userId, new HoldRequest(show.id(), seatIds));
                OrderResponse order = paymentService.createOrder(userId, booking.id());
                MockCheckoutResponse paid = paymentService.mockCheckout(userId, new MockCheckoutRequest(order.orderId(), true));
                paymentService.verify(userId, new VerifyPaymentRequest(order.orderId(), paid.paymentId(), paid.signature()));
                // spread them over the past week so the revenue chart has a shape
                int daysAgo = random.nextInt(7);
                jdbc.update("update bookings set created_at = created_at - interval ? day, confirmed_at = confirmed_at - interval ? day where id = ?",
                        daysAgo, daysAgo, booking.id());
                created++;
            } catch (RuntimeException e) {
                log.debug("Skipped a sample booking: {}", e.getMessage());
            }
        }
        return created;
    }

    private List<Long> pickAdjacentSeats(Long showId, int count, Random random) {
        List<SeatRow> rows = catalogService.seatMap(showId).rows();
        SeatRow row = rows.get(random.nextInt(rows.size()));
        List<SeatView> seats = row.seats();
        int start = random.nextInt(Math.max(1, seats.size() - count));
        List<Long> ids = new ArrayList<>();
        for (int i = start; i < Math.min(seats.size(), start + count); i++) {
            if (seats.get(i).status() != ShowSeatStatus.AVAILABLE) {
                return List.of();
            }
            ids.add(seats.get(i).id());
        }
        return ids;
    }
}
