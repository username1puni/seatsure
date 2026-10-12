package com.seatsure.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.seatsure.entity.Booking;
import com.seatsure.entity.BookingStatus;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"show", "show.movie", "show.screen", "show.screen.theatre", "show.screen.theatre.city"})
    @Query("select b from Booking b where b.user.id = :userId and b.status <> com.seatsure.entity.BookingStatus.EXPIRED order by b.createdAt desc")
    List<Booking> findForUser(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "show", "show.movie", "show.screen", "show.screen.theatre", "show.screen.theatre.city"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findDetailed(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "show", "show.movie", "show.screen", "show.screen.theatre"})
    Optional<Booking> findByBookingCode(String bookingCode);

    @Query("select b.id from Booking b where b.status = com.seatsure.entity.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt < :now order by b.holdExpiresAt")
    List<Long> findExpiredHoldIds(@Param("now") Instant now, Pageable page);

    @Query("select b.id from Booking b where b.user.id = :userId and b.show.id = :showId and b.status = com.seatsure.entity.BookingStatus.PENDING_PAYMENT")
    List<Long> findPendingIdsForUserAndShow(@Param("userId") Long userId, @Param("showId") Long showId);

    @Query("select b.id from Booking b where b.show.id = :showId and b.status in :statuses")
    List<Long> findIdsByShowAndStatus(@Param("showId") Long showId, @Param("statuses") List<BookingStatus> statuses);

    long countByStatus(BookingStatus status);

    // Revenue counts what was kept: paid amount minus any refund
    @Query("""
            select coalesce(sum(b.totalAmount - coalesce(b.refundAmount, 0)), 0) from Booking b
            where b.status in (com.seatsure.entity.BookingStatus.CONFIRMED, com.seatsure.entity.BookingStatus.CANCELLED)
              and b.confirmedAt is not null
            """)
    BigDecimal totalRevenue();

    @Query(value = """
            select date(convert_tz(b.confirmed_at, '+00:00', :offset)) as day,
                   sum(b.total_amount - coalesce(b.refund_amount, 0)) as revenue,
                   count(*) as bookings
            from bookings b
            where b.confirmed_at is not null and b.confirmed_at >= :since
            group by day order by day
            """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("since") Instant since, @Param("offset") String offset);

    @Query(value = """
            select s.id, m.title, t.name, s.start_time, count(bs.id) as seats_sold,
                   sum(bs.price) as revenue,
                   (select count(*) from show_seats x where x.show_id = s.id) as capacity
            from booking_seats bs
            join shows s on s.id = bs.show_id
            join movies m on m.id = s.movie_id
            join screens sc on sc.id = s.screen_id
            join theatres t on t.id = sc.theatre_id
            where bs.status = 'CONFIRMED'
            group by s.id, m.title, t.name, s.start_time
            order by seats_sold desc, s.start_time
            limit :limit
            """, nativeQuery = true)
    List<Object[]> mostBookedShows(@Param("limit") int limit);

    @Query(value = """
            select m.title, count(bs.id) as seats_sold, sum(bs.price) as revenue
            from booking_seats bs
            join shows s on s.id = bs.show_id
            join movies m on m.id = s.movie_id
            where bs.status = 'CONFIRMED'
            group by m.id, m.title
            order by seats_sold desc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> topMovies(@Param("limit") int limit);

    @EntityGraph(attributePaths = {"user", "show", "show.movie"})
    @Query("select b from Booking b where b.confirmedAt is not null order by b.confirmedAt desc")
    List<Booking> findRecentConfirmed(Pageable page);
}
