package com.seatsure.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.seatsure.entity.Movie;
import com.seatsure.entity.MovieKind;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findAllByOrderByTitleAsc();

    @Query("""
            select distinct m from Show s join s.movie m join s.screen sc join sc.theatre t
            where t.city.id = :cityId and s.status = com.seatsure.entity.ShowStatus.SCHEDULED
              and s.startTime >= :from and s.startTime < :to and m.active = true
              and (:kind is null or m.kind = :kind)
            order by m.title
            """)
    List<Movie> findPlaying(@Param("cityId") Long cityId, @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to, @Param("kind") MovieKind kind);
}
