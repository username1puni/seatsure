package com.seatsure.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.seatsure.entity.ShowSeat;
import com.seatsure.entity.ShowSeatStatus;

import jakarta.persistence.LockModeType;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @EntityGraph(attributePaths = "seat")
    List<ShowSeat> findByShowId(Long showId);

    /**
     * SELECT ... FOR UPDATE on the requested seats. Rows are always locked in id order so two
     * overlapping requests can't deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ShowSeat ss join fetch ss.seat
            where ss.showId = :showId and ss.seat.id in :seatIds
            order by ss.id
            """)
    List<ShowSeat> lockSeats(@Param("showId") Long showId, @Param("seatIds") Collection<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss join fetch ss.seat where ss.bookingId = :bookingId order by ss.id")
    List<ShowSeat> lockByBooking(@Param("bookingId") Long bookingId);

    List<ShowSeat> findByBookingId(Long bookingId);

    long countByShowIdAndStatus(Long showId, ShowSeatStatus status);

    long countByShowId(Long showId);

    @Query("""
            select ss.showId, count(ss) from ShowSeat ss
            where ss.showId in :showIds and ss.status = com.seatsure.entity.ShowSeatStatus.AVAILABLE
            group by ss.showId
            """)
    List<Object[]> countAvailable(@Param("showIds") Collection<Long> showIds);
}
