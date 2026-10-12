package com.seatsure.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        BigDecimal totalRevenue, long confirmedBookings, long cancelledBookings, long activeHolds, long upcomingShows,
        long users, List<DayRevenue> revenueByDay, List<TopShow> mostBookedShows, List<TopMovie> topMovies,
        List<RecentBooking> recentBookings) {
}
