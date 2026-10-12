package com.seatsure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.seatsure.entity.Show;
import com.seatsure.entity.ShowStatus;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theatre", "screen.theatre.city"})
    @Query("select s from Show s where s.id = :id")
    Optional<Show> findDetailed(@Param("id") Long id);

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theatre"})
    @Query("""
            select s from Show s
            where s.movie.id = :movieId and s.screen.theatre.city.id = :cityId
              and s.status = com.seatsure.entity.ShowStatus.SCHEDULED
              and s.startTime >= :from and s.startTime < :to
            order by s.screen.theatre.name, s.startTime
            """)
    List<Show> findForMovie(@Param("movieId") Long movieId, @Param("cityId") Long cityId,
                            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select distinct cast(s.startTime as LocalDate) from Show s
            where s.movie.id = :movieId and s.screen.theatre.city.id = :cityId
              and s.status = com.seatsure.entity.ShowStatus.SCHEDULED and s.startTime >= :from
            order by 1
            """)
    List<java.time.LocalDate> findShowDates(@Param("movieId") Long movieId, @Param("cityId") Long cityId,
                                            @Param("from") LocalDateTime from);

    @EntityGraph(attributePaths = {"movie", "screen", "screen.theatre"})
    @Query("""
            select s from Show s
            where s.startTime >= :from and s.startTime < :to
            order by s.startTime
            """)
    List<Show> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select count(s) > 0 from Show s
            where s.screen.id = :screenId and s.status = com.seatsure.entity.ShowStatus.SCHEDULED
              and s.startTime < :end and s.endTime > :start
            """)
    boolean overlaps(@Param("screenId") Long screenId, @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    boolean existsByScreenId(Long screenId);

    long countByStatusAndStartTimeAfter(ShowStatus status, LocalDateTime time);

    // one statement creates the seat inventory for a show, much faster than saving entities one by one
    @Modifying
    @Query(value = """
            insert into show_seats (show_id, seat_id, status, version)
            select :showId, id, 'AVAILABLE', 0 from seats where screen_id = :screenId
            """, nativeQuery = true)
    int createSeatInventory(@Param("showId") Long showId, @Param("screenId") Long screenId);
}
