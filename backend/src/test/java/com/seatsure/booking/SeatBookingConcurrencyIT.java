package com.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.HoldRequest;
import com.seatsure.entity.ShowSeat;
import com.seatsure.exception.SeatUnavailableException;
import com.seatsure.repository.ShowSeatRepository;
import com.seatsure.service.BookingService;
import com.seatsure.support.IntegrationTest;
import com.seatsure.support.TestData.ShowFixture;

/**
 * The main promise of the app: a seat can never be sold twice, however many people click at
 * the same moment. These run against a real MySQL 8 in Docker, because H2 locks rows
 * differently and would prove nothing.
 */
class SeatBookingConcurrencyIT extends IntegrationTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

    @Test
    void twentyUsersClickTheSameSeat_exactlyOneGetsIt() throws Exception {
        ShowFixture show = data.show(48);
        Long seat = show.row(2).get(4); // C5
        int threads = 20;
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            users.add(data.user("User " + i));
        }

        AtomicInteger won = new AtomicInteger();
        AtomicInteger seatTaken = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        runTogether(threads, i -> () -> {
            try {
                bookingService.holdSeats(users.get(i), new HoldRequest(show.showId(), List.of(seat)));
                won.incrementAndGet();
            } catch (SeatUnavailableException e) {
                seatTaken.incrementAndGet();
            } catch (Throwable t) {
                unexpected.add(t);
            }
            return null;
        });

        assertThat(unexpected).isEmpty();
        assertThat(won.get()).isEqualTo(1);
        assertThat(seatTaken.get()).isEqualTo(threads - 1);
        assertThat(data.count("select count(*) from show_seats where show_id = ? and status = 'HELD'", show.showId())).isEqualTo(1);
        assertThat(data.count("select count(*) from booking_seats where seat_id = ? and status in ('HELD','CONFIRMED')", seat)).isEqualTo(1);
        assertThat(data.count("select count(*) from bookings where status = 'PENDING_PAYMENT'")).isEqualTo(1);
    }

    @Test
    void overlappingSelections_neverShareASeat_andAreAllOrNothing() throws Exception {
        ShowFixture show = data.show(48);
        List<Long> row = show.row(0);
        int threads = 16;
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            users.add(data.user("Group " + i));
        }

        // every user wants 3 seats next to each other, and most windows overlap with others
        List<BookingResponse> winners = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        runTogether(threads, i -> () -> {
            int start = i % 8;
            try {
                winners.add(bookingService.holdSeats(users.get(i),
                        new HoldRequest(show.showId(), row.subList(start, start + 3))));
            } catch (SeatUnavailableException expected) {
                // lost the race for at least one seat, so got none
            } catch (Throwable t) {
                unexpected.add(t);
            }
            return null;
        });

        assertThat(unexpected).isEmpty();
        assertThat(winners).isNotEmpty();
        List<String> sold = winners.stream().flatMap(b -> b.seats().stream()).toList();
        assertThat(sold).doesNotHaveDuplicates();
        assertThat(winners).allSatisfy(b -> assertThat(b.seatCount()).isEqualTo(3));
        int held = data.count("select count(*) from show_seats where show_id = ? and status = 'HELD'", show.showId());
        assertThat(held).isEqualTo(winners.size() * 3);
        assertThat(data.count("select count(*) from booking_seats where status = 'HELD'")).isEqualTo(held);
    }

    @Test
    void uniqueKeyRejectsASecondLiveRowForTheSameSeat() {
        ShowFixture show = data.show(48);
        Long seat = show.row(1).get(0);
        Long userA = data.user("A");
        Long userB = data.user("B");
        BookingResponse first = bookingService.holdSeats(userA, new HoldRequest(show.showId(), List.of(seat)));
        BookingResponse second = bookingService.holdSeats(userB, new HoldRequest(show.showId(), List.of(show.row(1).get(1))));

        // go around the service, as a buggy future feature might, and try to give the seat to B too
        String insert = "insert into booking_seats (booking_id, show_id, seat_id, price, status) values (?, ?, ?, 200, ?)";
        assertThatThrownBy(() -> jdbc.update(insert, second.id(), show.showId(), seat, "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_booking_seats_active");

        // released and cancelled rows don't count, so history can pile up for the same seat
        jdbc.update("update booking_seats set status = 'RELEASED' where booking_id = ?", first.id());
        jdbc.update(insert, second.id(), show.showId(), seat, "CANCELLED");
        jdbc.update(insert, second.id(), show.showId(), seat, "CONFIRMED");
        assertThat(data.count("select count(*) from booking_seats where seat_id = ?", seat)).isEqualTo(3);
    }

    @Test
    void staleUpdateOfAShowSeatIsRejectedByVersion() {
        ShowFixture show = data.show(48);
        Long rowId = jdbc.queryForObject("select id from show_seats where show_id = ? and seat_id = ?", Long.class,
                show.showId(), show.row(0).get(0));
        ShowSeat copyA = tx.execute(s -> showSeatRepository.findById(rowId).orElseThrow());
        ShowSeat copyB = tx.execute(s -> showSeatRepository.findById(rowId).orElseThrow());

        copyA.book();
        showSeatRepository.save(copyA);

        copyB.free(); // B still thinks it is looking at version 0
        assertThatThrownBy(() -> showSeatRepository.save(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(jdbc.queryForObject("select status from show_seats where id = ?", String.class, rowId)).isEqualTo("BOOKED");
    }

    interface Task {
        Callable<Void> forThread(int index);
    }

    /** Starts all tasks at the same instant using a latch, then waits for every one to finish. */
    private static void runTogether(int threads, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Void> body = task.forThread(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return body.call();
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        for (Future<Void> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }
}
