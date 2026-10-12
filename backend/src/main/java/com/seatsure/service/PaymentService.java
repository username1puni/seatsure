package com.seatsure.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.MockCheckoutRequest;
import com.seatsure.dto.MockCheckoutResponse;
import com.seatsure.dto.OrderResponse;
import com.seatsure.dto.PaymentConfigResponse;
import com.seatsure.dto.PaymentFailureRequest;
import com.seatsure.dto.VerifyPaymentRequest;
import com.seatsure.entity.Booking;
import com.seatsure.entity.BookingStatus;
import com.seatsure.entity.Payment;
import com.seatsure.entity.PaymentStatus;
import com.seatsure.exception.BadRequestException;
import com.seatsure.exception.ConflictException;
import com.seatsure.exception.ForbiddenException;
import com.seatsure.exception.NotFoundException;
import com.seatsure.exception.PaymentException;
import com.seatsure.repository.BookingRepository;
import com.seatsure.repository.PaymentRepository;
import com.seatsure.service.payment.GatewayOrder;
import com.seatsure.service.payment.MockPaymentGateway;
import com.seatsure.service.payment.PaymentGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Payment steps. Calls to the gateway are kept outside database transactions on purpose:
 * a slow network call should never hold row locks on seats.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentConfigResponse config() {
        return new PaymentConfigResponse(gateway.type(), gateway.publicKey());
    }

    public OrderResponse createOrder(Long userId, Long bookingId) {
        Booking booking = tx.execute(status -> {
            Booking b = bookingRepository.findDetailed(bookingId)
                    .orElseThrow(() -> new NotFoundException("Booking not found"));
            checkOwner(b, userId);
            b.getShow().getMovie().getTitle();
            return b;
        });
        if (!booking.isPending()) {
            throw new ConflictException("This booking is no longer waiting for payment");
        }
        if (booking.holdExpired(Instant.now(clock))) {
            throw new ConflictException("Your seat hold has expired. Please pick your seats again.");
        }

        GatewayOrder order = gateway.createOrder(booking.getTotalAmount(), booking.getBookingCode());

        tx.executeWithoutResult(status -> {
            Payment payment = new Payment();
            payment.setBooking(bookingRepository.getReferenceById(bookingId));
            payment.setGateway(gateway.type());
            payment.setGatewayOrderId(order.orderId());
            payment.setAmount(booking.getTotalAmount());
            payment.setStatus(PaymentStatus.CREATED);
            paymentRepository.save(payment);
        });

        String description = booking.getShow().getMovie().getTitle() + " - " + booking.getSeatCount()
                + (booking.getSeatCount() == 1 ? " ticket" : " tickets");
        return new OrderResponse(gateway.type(), gateway.publicKey(), order.orderId(), booking.getTotalAmount(),
                order.amountPaise(), order.currency(), booking.getId(), booking.getBookingCode(),
                booking.getHoldExpiresAt(), description);
    }

    /**
     * Called with what the checkout returned. Confirms the booking only if the signature is
     * genuine. If the money was taken but the seats can't be confirmed any more (hold expired),
     * the payment is refunded straight away.
     */
    public BookingResponse verify(Long userId, VerifyPaymentRequest request) {
        Payment payment = loadOwnedPayment(userId, request.orderId());
        Long bookingId = payment.getBooking().getId();

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            // double submit from the browser, the first one already went through
            return bookingService.get(userId, false, bookingId);
        }
        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new ConflictException("This payment was already closed");
        }
        if (!gateway.verifySignature(request.orderId(), request.paymentId(), request.signature())) {
            log.warn("Bad payment signature for order {}", request.orderId());
            markFailed(payment.getId(), request.paymentId(), "Signature verification failed");
            throw new PaymentException("We could not verify this payment. No seats were booked.");
        }

        try {
            return bookingService.confirmPaid(bookingId, payment.getId(), request.paymentId());
        } catch (RuntimeException e) {
            log.warn("Payment {} succeeded but booking {} could not be confirmed: {}",
                    request.paymentId(), bookingId, e.getMessage());
            refundAfterFailedConfirm(payment.getId(), request.paymentId(), e.getMessage());
            throw new PaymentException("Your seats could not be confirmed (" + e.getMessage()
                    + "). The full amount has been refunded.");
        }
    }

    /** Checkout closed, card declined or user gave up: release the seats now. */
    public void reportFailure(Long userId, PaymentFailureRequest request) {
        Payment payment = loadOwnedPayment(userId, request.orderId());
        if (payment.getStatus() != PaymentStatus.CREATED) {
            return;
        }
        String reason = request.reason() == null ? "Payment was not completed" : request.reason();
        markFailed(payment.getId(), null, reason);
    }

    /** Only used with the mock gateway: plays the part of the Razorpay checkout popup. */
    public MockCheckoutResponse mockCheckout(Long userId, MockCheckoutRequest request) {
        if (!(gateway instanceof MockPaymentGateway mock)) {
            throw new BadRequestException("The mock checkout is only available when PAYMENT_GATEWAY=mock");
        }
        loadOwnedPayment(userId, request.orderId());
        if (!request.success()) {
            return new MockCheckoutResponse(request.orderId(), null, null, false, "Payment declined by the bank (test)");
        }
        MockPaymentGateway.MockPayment paid = mock.pay(request.orderId());
        return new MockCheckoutResponse(request.orderId(), paid.paymentId(), paid.signature(), true, null);
    }

    private void markFailed(Long paymentId, String gatewayPaymentId, String reason) {
        tx.executeWithoutResult(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayPaymentId(gatewayPaymentId);
            payment.setFailureReason(reason);
        });
        bookingService.releaseHold(paymentRepository.findById(paymentId).orElseThrow().getBooking().getId(),
                BookingStatus.PAYMENT_FAILED);
    }

    private void refundAfterFailedConfirm(Long paymentId, String gatewayPaymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        String refundId = gateway.refund(gatewayPaymentId, payment.getAmount());
        tx.executeWithoutResult(status -> {
            Payment p = paymentRepository.findById(paymentId).orElseThrow();
            p.setStatus(PaymentStatus.REFUNDED);
            p.setGatewayPaymentId(gatewayPaymentId);
            p.setRefundId(refundId);
            p.setRefundAmount(p.getAmount());
            p.setFailureReason(reason);
        });
        bookingService.releaseHold(payment.getBooking().getId(), BookingStatus.PAYMENT_FAILED);
    }

    private Payment loadOwnedPayment(Long userId, String orderId) {
        return tx.execute(status -> {
            Payment payment = paymentRepository.findByGatewayOrderId(orderId)
                    .orElseThrow(() -> new NotFoundException("Payment order not found"));
            checkOwner(payment.getBooking(), userId);
            return payment;
        });
    }

    private static void checkOwner(Booking booking, Long userId) {
        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your booking");
        }
    }
}
