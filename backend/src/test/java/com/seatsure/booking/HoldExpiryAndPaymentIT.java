package com.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.HoldRequest;
import com.seatsure.dto.MockCheckoutRequest;
import com.seatsure.dto.MockCheckoutResponse;
import com.seatsure.dto.OrderResponse;
import com.seatsure.dto.PaymentFailureRequest;
import com.seatsure.dto.VerifyPaymentRequest;
import com.seatsure.exception.PaymentException;
import com.seatsure.exception.SeatUnavailableException;
import com.seatsure.service.BookingService;
import com.seatsure.service.HoldExpiryJob;
import com.seatsure.service.PaymentService;
import com.seatsure.support.IntegrationTest;
import com.seatsure.support.TestData.ShowFixture;

class HoldExpiryAndPaymentIT extends IntegrationTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private HoldExpiryJob expiryJob;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void expiredHoldIsReleasedAndTheSeatCanBeBookedAgain() {
        ShowFixture show = data.show(48);
        Long seat = show.row(0).get(0);
        Long slowUser = data.user("Slow");
        Long nextUser = data.user("Next");
        BookingResponse hold = bookingService.holdSeats(slowUser, new HoldRequest(show.showId(), List.of(seat)));

        assertThatThrownBy(() -> bookingService.holdSeats(nextUser, new HoldRequest(show.showId(), List.of(seat))))
                .isInstanceOf(SeatUnavailableException.class);

        data.expireHold(hold.id());
        assertThat(expiryJob.releaseExpiredHolds()).isEqualTo(1);

        assertThat(status(hold.id())).isEqualTo("EXPIRED");
        assertThat(data.count("select count(*) from show_seats where show_id = ? and status = 'AVAILABLE'", show.showId()))
                .isEqualTo(50);
        BookingResponse second = bookingService.holdSeats(nextUser, new HoldRequest(show.showId(), List.of(seat)));
        assertThat(second.seats()).containsExactly("A1");
    }

    @Test
    void jobLeavesHoldsThatAreStillValid() {
        ShowFixture show = data.show(48);
        bookingService.holdSeats(data.user("Paying"), new HoldRequest(show.showId(), show.row(0).subList(0, 2)));
        assertThat(expiryJob.releaseExpiredHolds()).isZero();
        assertThat(data.count("select count(*) from show_seats where status = 'HELD'")).isEqualTo(2);
    }

    @Test
    void fullPaymentFlowConfirmsTheBooking() {
        ShowFixture show = data.show(48);
        Long user = data.user("Payer");
        BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), show.row(4).subList(0, 2)));
        assertThat(hold.totalAmount()).isEqualByComparingTo("700"); // row E is premium, 350 each

        BookingResponse confirmed = pay(user, hold.id());

        assertThat(confirmed.status().name()).isEqualTo("CONFIRMED");
        assertThat(data.count("select count(*) from show_seats where status = 'BOOKED'")).isEqualTo(2);
        assertThat(data.count("select count(*) from booking_seats where status = 'CONFIRMED'")).isEqualTo(2);
        assertThat(data.count("select count(*) from payments where status = 'SUCCESS'")).isEqualTo(1);
    }

    @Test
    void forgedSignatureIsRejectedAndSeatsAreReleased() {
        ShowFixture show = data.show(48);
        Long user = data.user("Cheater");
        BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), List.of(show.row(0).get(0))));
        OrderResponse order = paymentService.createOrder(user, hold.id());

        assertThatThrownBy(() -> paymentService.verify(user,
                new VerifyPaymentRequest(order.orderId(), "pay_fake", "0".repeat(64))))
                .isInstanceOf(PaymentException.class);

        assertThat(status(hold.id())).isEqualTo("PAYMENT_FAILED");
        assertThat(data.count("select count(*) from show_seats where status = 'AVAILABLE'")).isEqualTo(50);
        assertThat(data.count("select count(*) from payments where status = 'FAILED'")).isEqualTo(1);
    }

    @Test
    void declinedPaymentReleasesSeatsRightAway() {
        ShowFixture show = data.show(48);
        Long user = data.user("Declined");
        BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), show.row(1).subList(0, 3)));
        OrderResponse order = paymentService.createOrder(user, hold.id());
        MockCheckoutResponse declined = paymentService.mockCheckout(user, new MockCheckoutRequest(order.orderId(), false));
        assertThat(declined.success()).isFalse();

        paymentService.reportFailure(user, new PaymentFailureRequest(order.orderId(), declined.error()));

        assertThat(status(hold.id())).isEqualTo("PAYMENT_FAILED");
        assertThat(data.count("select count(*) from show_seats where status = 'HELD'")).isZero();
    }

    @Test
    void paymentThatArrivesAfterTheHoldExpiredIsRefunded() {
        ShowFixture show = data.show(48);
        Long user = data.user("Late");
        BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), List.of(show.row(0).get(5))));
        OrderResponse order = paymentService.createOrder(user, hold.id());
        MockCheckoutResponse paid = paymentService.mockCheckout(user, new MockCheckoutRequest(order.orderId(), true));

        data.expireHold(hold.id()); // the user sat on the payment page too long

        assertThatThrownBy(() -> paymentService.verify(user,
                new VerifyPaymentRequest(order.orderId(), paid.paymentId(), paid.signature())))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("refunded");
        assertThat(status(hold.id())).isEqualTo("PAYMENT_FAILED");
        assertThat(jdbc.queryForObject("select status from payments where gateway_order_id = ?", String.class, order.orderId()))
                .isEqualTo("REFUNDED");
        assertThat(data.count("select count(*) from show_seats where status <> 'AVAILABLE'")).isZero();
    }

    /**
     * The nastiest race: the user pays at the very last second while the expiry job is
     * releasing the same booking. Whoever commits first wins and the other backs off, so the
     * end state is always one of the two clean outcomes, never a mix.
     */
    @Test
    void paymentRacingTheExpiryJobAlwaysEndsInACleanState() throws Exception {
        ShowFixture show = data.show(48);
        Long user = data.user("Racer");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                Long seat = show.seatIds().get(round);
                BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), List.of(seat)));
                OrderResponse order = paymentService.createOrder(user, hold.id());
                MockCheckoutResponse paid = paymentService.mockCheckout(user, new MockCheckoutRequest(order.orderId(), true));
                Long paymentRow = jdbc.queryForObject("select id from payments where gateway_order_id = ?", Long.class, order.orderId());
                // the hold runs out a few milliseconds from now, right while both sides are working
                jdbc.update("update bookings set hold_expires_at = utc_timestamp(6) + interval 40000 microsecond where id = ?", hold.id());
                int delay = round * 8;

                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> both = new ArrayList<>();
                both.add(pool.submit(() -> {
                    go.await();
                    Thread.sleep(delay);
                    try {
                        bookingService.confirmPaid(hold.id(), paymentRow, paid.paymentId());
                    } catch (RuntimeException lost) {
                        // too late, the hold had expired
                    }
                    return null;
                }));
                both.add(pool.submit(() -> {
                    go.await();
                    long until = System.currentTimeMillis() + 1000;
                    while (System.currentTimeMillis() < until && status(hold.id()).equals("PENDING_PAYMENT")) {
                        expiryJob.releaseExpiredHolds();
                        Thread.sleep(5);
                    }
                    return null;
                }));
                go.countDown();
                for (Future<?> f : both) {
                    f.get(30, TimeUnit.SECONDS);
                }

                String booking = status(hold.id());
                String seatStatus = jdbc.queryForObject("select status from show_seats where show_id = ? and seat_id = ?",
                        String.class, show.showId(), seat);
                if (booking.equals("CONFIRMED")) {
                    assertThat(seatStatus).isEqualTo("BOOKED");
                } else {
                    assertThat(booking).isEqualTo("EXPIRED");
                    assertThat(seatStatus).isEqualTo("AVAILABLE");
                }
                assertThat(data.count("select count(*) from booking_seats where seat_id = ? and status in ('HELD','CONFIRMED')", seat))
                        .isEqualTo(booking.equals("CONFIRMED") ? 1 : 0);
            }
        } finally {
            pool.shutdown();
        }
    }

    private BookingResponse pay(Long user, Long bookingId) {
        OrderResponse order = paymentService.createOrder(user, bookingId);
        MockCheckoutResponse paid = paymentService.mockCheckout(user, new MockCheckoutRequest(order.orderId(), true));
        return paymentService.verify(user, new VerifyPaymentRequest(order.orderId(), paid.paymentId(), paid.signature()));
    }

    private String status(Long bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }
}
