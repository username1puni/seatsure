package com.seatsure.service.admin;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatsure.dto.DashboardResponse;
import com.seatsure.dto.DayRevenue;
import com.seatsure.dto.RecentBooking;
import com.seatsure.dto.TopMovie;
import com.seatsure.dto.TopShow;
import com.seatsure.entity.BookingStatus;
import com.seatsure.entity.ShowStatus;
import com.seatsure.repository.BookingRepository;
import com.seatsure.repository.ShowRepository;
import com.seatsure.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DAYS = 7;

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public DashboardResponse dashboard() {
        LocalDate today = LocalDate.now(clock);
        ZoneOffset offset = clock.getZone().getRules().getOffset(clock.instant());
        LocalDate firstDay = today.minusDays(DAYS - 1);

        Map<LocalDate, DayRevenue> byDay = new HashMap<>();
        var since = firstDay.atStartOfDay().toInstant(offset);
        for (Object[] row : bookingRepository.revenueByDay(since, offset.getId().equals("Z") ? "+00:00" : offset.getId())) {
            LocalDate day = toLocalDate(row[0]);
            byDay.put(day, new DayRevenue(day, toBigDecimal(row[1]), toLong(row[2])));
        }
        List<DayRevenue> revenueByDay = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            LocalDate day = firstDay.plusDays(i);
            revenueByDay.add(byDay.getOrDefault(day, new DayRevenue(day, BigDecimal.ZERO, 0)));
        }

        List<TopShow> topShows = bookingRepository.mostBookedShows(5).stream()
                .map(r -> new TopShow(toLong(r[0]), (String) r[1], (String) r[2], toLocalDateTime(r[3]), toLong(r[4]),
                        toLong(r[6]), toBigDecimal(r[5])))
                .toList();
        List<TopMovie> topMovies = bookingRepository.topMovies(5).stream()
                .map(r -> new TopMovie((String) r[0], toLong(r[1]), toBigDecimal(r[2])))
                .toList();
        List<RecentBooking> recent = bookingRepository.findRecentConfirmed(PageRequest.of(0, 8)).stream()
                .map(b -> new RecentBooking(b.getBookingCode(), b.getUser().getFullName(), b.getShow().getMovie().getTitle(),
                        b.getSeatCount(), b.getTotalAmount(), b.getStatus(), b.getConfirmedAt()))
                .toList();

        long upcomingShows = showRepository.countByStatusAndStartTimeAfter(ShowStatus.SCHEDULED, LocalDateTime.now(clock));
        return new DashboardResponse(bookingRepository.totalRevenue(),
                bookingRepository.countByStatus(BookingStatus.CONFIRMED),
                bookingRepository.countByStatus(BookingStatus.CANCELLED),
                bookingRepository.countByStatus(BookingStatus.PENDING_PAYMENT),
                upcomingShows, userRepository.count(), revenueByDay, topShows, topMovies, recent);
    }

    // native queries hand back driver types, these keep the mapping in one place
    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return (LocalDate) value;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }
}
