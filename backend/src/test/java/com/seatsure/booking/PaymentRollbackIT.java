package com.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.HoldRequest;
import com.seatsure.dto.MockCheckoutRequest;
import com.seatsure.dto.MockCheckoutResponse;
import com.seatsure.dto.OrderResponse;
import com.seatsure.dto.VerifyPaymentRequest;
import com.seatsure.exception.PaymentException;
import com.seatsure.service.BookingService;
import com.seatsure.service.PaymentService;
import com.seatsure.service.TicketService;
import com.seatsure.support.IntegrationTest;
import com.seatsure.support.TestData.ShowFixture;

/** If anything breaks halfway through confirming a payment, nothing of it may stay in the database. */
class PaymentRollbackIT extends IntegrationTest {

    @MockitoSpyBean
    private TicketService ticketService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void failureInsideConfirmRollsBackEveryWrite() {
        ShowFixture show = data.show(48);
        Long user = data.user("Unlucky");
        BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), show.row(0).subList(0, 2)));
        OrderResponse order = paymentService.createOrder(user, hold.id());
        Long paymentRow = jdbc.queryForObject("select id from payments where gateway_order_id = ?", Long.class, order.orderId());

        // fails after the seats, booking and payment were already changed in memory
        doThrow(new IllegalStateException("disk full")).when(ticketService).newTicketToken();

        assertThatThrownBy(() -> bookingService.confirmPaid(hold.id(), paymentRow, "pay_123"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, hold.id()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select status from payments where id = ?", String.class, paymentRow))
                .isEqualTo("CREATED");
        assertThat(data.count("select count(*) from show_seats where status = 'HELD'")).isEqualTo(2);
        assertThat(data.count("select count(*) from show_seats where status = 'BOOKED'")).isZero();
        assertThat(data.count("select count(*) from booking_seats where status = 'CONFIRMED'")).isZero();
    }

    @Test
    void userIsRefundedWhenConfirmFailsAfterPaying() {
        ShowFixture show = data.show(48);
        Long user = data.user("Refunded");
        BookingResponse hold = bookingService.holdSeats(user, new HoldRequest(show.showId(), List.of(show.row(3).get(3))));
        OrderResponse order = paymentService.createOrder(user, hold.id());
        MockCheckoutResponse paid = paymentService.mockCheckout(user, new MockCheckoutRequest(order.orderId(), true));
        doThrow(new IllegalStateException("disk full")).when(ticketService).newTicketToken();

        assertThatThrownBy(() -> paymentService.verify(user,
                new VerifyPaymentRequest(order.orderId(), paid.paymentId(), paid.signature())))
                .isInstanceOf(PaymentException.class);

        assertThat(jdbc.queryForObject("select status from payments where gateway_order_id = ?", String.class, order.orderId()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, hold.id()))
                .isEqualTo("PAYMENT_FAILED");
        assertThat(data.count("select count(*) from show_seats where status <> 'AVAILABLE'")).isZero();
    }
}
