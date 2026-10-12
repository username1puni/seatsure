package com.seatsure.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.HoldRequest;
import com.seatsure.dto.RefundPreview;
import com.seatsure.dto.SeatState;
import com.seatsure.entity.Booking;
import com.seatsure.entity.BookingSeat;
import com.seatsure.entity.BookingSeatStatus;
import com.seatsure.entity.BookingStatus;
import com.seatsure.entity.Payment;
import com.seatsure.entity.PaymentStatus;
import com.seatsure.entity.Seat;
import com.seatsure.entity.Show;
import com.seatsure.entity.ShowSeat;
import com.seatsure.entity.ShowSeatStatus;
import com.seatsure.entity.ShowStatus;
import com.seatsure.exception.BadRequestException;
import com.seatsure.exception.ConflictException;
import com.seatsure.exception.ForbiddenException;
import com.seatsure.exception.NotFoundException;
import com.seatsure.exception.SeatUnavailableException;
import com.seatsure.repository.BookingRepository;
import com.seatsure.repository.PaymentRepository;
import com.seatsure.repository.ShowRepository;
import com.seatsure.repository.ShowSeatRepository;
import com.seatsure.repository.UserRepository;
import com.seatsure.service.payment.PaymentGateway;

import lombok.extern.slf4j.Slf4j;

/**
 * Everything that changes seat state goes through here.
 *
 * Lock order is always booking row first, then show_seats rows in id order. Holding a new
 * booking only locks show_seats (the booking does not exist yet). Keeping one order everywhere
 * is what keeps these transactions from deadlocking each other.
 */
@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final BookingCodeGenerator codeGenerator;
    private final TicketService ticketService;
    private final RefundPolicy refundPolicy;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration holdDuration;
    private final int maxSeats;

    public BookingService(BookingRepository bookingRepository, ShowRepository showRepository,
                          ShowSeatRepository showSeatRepository, UserRepository userRepository,
                          PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                          BookingCodeGenerator codeGenerator, TicketService ticketService, RefundPolicy refundPolicy,
                          ApplicationEventPublisher events, Clock clock,
                          @Value("${app.booking.hold-minutes}") int holdMinutes,
                          @Value("${app.booking.max-seats}") int maxSeats) {
        this.bookingRepository = bookingRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.codeGenerator = codeGenerator;
        this.ticketService = ticketService;
        this.refundPolicy = refundPolicy;
        this.events = events;
        this.clock = clock;
        this.holdDuration = Duration.ofMinutes(holdMinutes);
        this.maxSeats = maxSeats;
    }

    /**
     * Holds the seats for this user for a few minutes while they pay. Either every requested
     * seat is held or none is.
     */
    @Transactional
    public BookingResponse holdSeats(Long userId, HoldRequest request) {
        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();
        if (seatIds.isEmpty() || seatIds.size() > maxSeats) {
            throw new BadRequestException("You can book 1 to " + maxSeats + " seats at a time");
        }
        Show show = showRepository.findById(request.showId())
                .orElseThrow(() -> new NotFoundException("Show not found"));
        if (show.getStatus() != ShowStatus.SCHEDULED) {
            throw new BadRequestException("This show has been cancelled");
        }
        if (!show.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw new BadRequestException("This show has already started");
        }

        // If the user went back and picked seats again, let go of their previous hold first
        for (Long oldBookingId : bookingRepository.findPendingIdsForUserAndShow(userId, show.getId())) {
            releaseHold(oldBookingId, BookingStatus.CANCELLED);
        }

        // SELECT ... FOR UPDATE. A second request for any of these seats waits here until we commit.
        List<ShowSeat> locked = showSeatRepository.lockSeats(show.getId(), seatIds);
        if (locked.size() != seatIds.size()) {
            throw new BadRequestException("Some of the selected seats are not part of this show");
        }
        List<String> taken = locked.stream()
                .filter(s -> s.getStatus() != ShowSeatStatus.AVAILABLE)
                .map(s -> s.getSeat().getLabel())
                .toList();
        if (!taken.isEmpty()) {
            throw new SeatUnavailableException(taken);
        }

        Instant now = Instant.now(clock);
        Booking booking = new Booking();
        booking.setBookingCode(codeGenerator.next());
        booking.setUser(userRepository.getReferenceById(userId));
        booking.setShow(show);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(now.plus(holdDuration));
        booking.setCreatedAt(now);
        BigDecimal total = BigDecimal.ZERO;
        for (ShowSeat showSeat : locked) {
            BigDecimal price = show.priceFor(showSeat.getSeat().getCategory());
            booking.addSeat(new BookingSeat(show.getId(), showSeat.getSeat(), price));
            total = total.add(price);
        }
        booking.setSeatCount(locked.size());
        booking.setTotalAmount(total);
        // flush now so the unique key on booking_seats is checked inside this transaction
        bookingRepository.saveAndFlush(booking);

        for (ShowSeat showSeat : locked) {
            showSeat.hold(booking.getId(), booking.getHoldExpiresAt());
        }
        publishSeats(show.getId(), locked, ShowSeatStatus.HELD);
        log.info("Booking {} holds {} seat(s) of show {} until {}", booking.getBookingCode(), locked.size(),
                show.getId(), booking.getHoldExpiresAt());
        return toResponse(booking);
    }

    /** User pressed back / payment failed: free the seats right away instead of waiting for expiry. */
    @Transactional
    public boolean releaseHold(Long bookingId, BookingStatus newStatus) {
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.isPending()) {
            return false;
        }
        booking.setStatus(newStatus);
        freeSeats(booking, BookingSeatStatus.RELEASED);
        return true;
    }

    @Transactional
    public void cancelHoldForUser(Long userId, Long bookingId) {
        Booking booking = lockOwned(userId, bookingId);
        if (!booking.isPending()) {
            throw new BadRequestException("This booking is not waiting for payment");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        freeSeats(booking, BookingSeatStatus.RELEASED);
    }

    /**
     * Called by the expiry job. Uses optimistic locking instead of SELECT FOR UPDATE: the job
     * reads the booking without a lock and the UPDATE only succeeds if the version is unchanged.
     * If the payment confirmed this booking a moment earlier, the version moved on and this
     * throws ObjectOptimisticLockingFailureException, which the job treats as "already handled".
     */
    @Transactional
    public boolean expireHold(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || !booking.isPending() || !booking.holdExpired(Instant.now(clock))) {
            return false;
        }
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.saveAndFlush(booking);
        freeSeats(booking, BookingSeatStatus.RELEASED);
        return true;
    }

    /**
     * Turns a paid hold into a confirmed booking. Payment row, booking, seats and ticket are
     * written in one transaction, so a failure anywhere leaves everything as it was.
     */
    @Transactional
    public BookingResponse confirmPaid(Long bookingId, Long paymentRowId, String gatewayPaymentId) {
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.isPending()) {
            throw new ConflictException("This booking is " + describe(booking.getStatus()) + " and can't be paid for");
        }
        Instant now = Instant.now(clock);
        if (booking.holdExpired(now)) {
            throw new ConflictException("Your seat hold expired before the payment finished");
        }
        List<ShowSeat> seats = showSeatRepository.lockByBooking(bookingId);
        boolean allHeld = seats.size() == booking.getSeatCount()
                && seats.stream().allMatch(s -> s.getStatus() == ShowSeatStatus.HELD);
        if (!allHeld) {
            // should never happen, the hold owns these rows. Fail loudly rather than sell a seat twice.
            throw new IllegalStateException("Seats of booking " + bookingId + " are no longer held");
        }

        Payment payment = paymentRepository.findById(paymentRowId).orElseThrow();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(gatewayPaymentId);

        seats.forEach(ShowSeat::book);
        booking.getSeats().forEach(s -> s.setStatus(BookingSeatStatus.CONFIRMED));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);
        booking.setTicketToken(ticketService.newTicketToken());

        publishSeats(booking.getShow().getId(), seats, ShowSeatStatus.BOOKED);
        events.publishEvent(new BookingConfirmedEvent(booking.getId()));
        log.info("Booking {} confirmed", booking.getBookingCode());
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public RefundPreview refundPreview(Long userId, Long bookingId) {
        Booking booking = ownedBooking(userId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return new RefundPreview(bookingId, false, 0, BigDecimal.ZERO, "Only confirmed bookings can be cancelled");
        }
        int percent = refundPolicy.refundPercent(booking.getShow().getStartTime(), LocalDateTime.now(clock));
        if (percent == 0) {
            return new RefundPreview(bookingId, false, 0, BigDecimal.ZERO,
                    "Bookings can't be cancelled less than 2 hours before the show");
        }
        BigDecimal amount = refundPolicy.refundAmount(booking.getTotalAmount(), percent);
        return new RefundPreview(bookingId, true, percent, amount,
                percent == 100 ? "Full refund" : percent + "% refund, as the show is less than 24 hours away");
    }

    /** User cancels a confirmed booking. The refund call is part of the transaction: no refund, no cancel. */
    @Transactional
    public BookingResponse cancelByUser(Long userId, Long bookingId) {
        Booking booking = lockOwned(userId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed bookings can be cancelled");
        }
        int percent = refundPolicy.refundPercent(booking.getShow().getStartTime(), LocalDateTime.now(clock));
        if (percent == 0) {
            throw new BadRequestException("Bookings can't be cancelled less than 2 hours before the show");
        }
        cancelConfirmed(booking, percent);
        return toResponse(booking);
    }

    /** Used when an admin cancels a whole show. Everyone gets their full money back. */
    @Transactional
    public void cancelForCancelledShow(Long bookingId) {
        Booking booking = bookingRepository.lockById(bookingId).orElseThrow();
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            cancelConfirmed(booking, 100);
        } else if (booking.isPending()) {
            booking.setStatus(BookingStatus.CANCELLED);
            freeSeats(booking, BookingSeatStatus.RELEASED);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long userId) {
        return bookingRepository.findForUser(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long userId, boolean admin, Long bookingId) {
        Booking booking = admin ? bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found")) : ownedBooking(userId, bookingId);
        return toResponse(booking);
    }

    /** Throws unless this user may see the booking (owner or admin). */
    @Transactional(readOnly = true)
    public void checkAccess(Long userId, boolean admin, Long bookingId) {
        if (admin) {
            if (!bookingRepository.existsById(bookingId)) {
                throw new NotFoundException("Booking not found");
            }
            return;
        }
        ownedBooking(userId, bookingId);
    }

    private void cancelConfirmed(Booking booking, int percent) {
        BigDecimal refund = refundPolicy.refundAmount(booking.getTotalAmount(), percent);
        Payment payment = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), PaymentStatus.SUCCESS)
                .orElseThrow(() -> new IllegalStateException("No successful payment for booking " + booking.getId()));
        String refundId = paymentGateway.refund(payment.getGatewayPaymentId(), refund);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundId(refundId);
        payment.setRefundAmount(refund);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now(clock));
        booking.setRefundAmount(refund);
        freeSeats(booking, BookingSeatStatus.CANCELLED);
        log.info("Booking {} cancelled, refunded {} ({}%)", booking.getBookingCode(), refund, percent);
    }

    private void freeSeats(Booking booking, BookingSeatStatus seatStatus) {
        List<ShowSeat> seats = showSeatRepository.findByBookingId(booking.getId()).stream()
                .sorted(Comparator.comparing(ShowSeat::getId))
                .toList();
        seats.forEach(ShowSeat::free);
        booking.getSeats().forEach(s -> s.setStatus(seatStatus));
        publishSeats(booking.getShow().getId(), seats, ShowSeatStatus.AVAILABLE);
    }

    // lock first, check owner after: reading before locking could act on a stale status
    private Booking lockOwned(Long userId, Long bookingId) {
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your booking");
        }
        return booking;
    }

    private Booking ownedBooking(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your booking");
        }
        return booking;
    }

    private void publishSeats(Long showId, List<ShowSeat> seats, ShowSeatStatus status) {
        if (seats.isEmpty()) {
            return;
        }
        List<SeatState> states = seats.stream().map(s -> new SeatState(s.getSeat().getId(), status)).toList();
        events.publishEvent(new SeatsChangedEvent(showId, states));
    }

    private static String describe(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "already paid";
            case EXPIRED -> "expired";
            case CANCELLED -> "cancelled";
            case PAYMENT_FAILED -> "closed after a failed payment";
            case PENDING_PAYMENT -> "pending";
        };
    }

    BookingResponse toResponse(Booking b) {
        Show show = b.getShow();
        List<String> labels = b.getSeats().stream()
                .map(BookingSeat::getSeat)
                .sorted(Seat.DISPLAY_ORDER)
                .map(Seat::getLabel)
                .toList();
        boolean cancellable = b.getStatus() == BookingStatus.CONFIRMED
                && refundPolicy.refundPercent(show.getStartTime(), LocalDateTime.now(clock)) > 0;
        var movie = show.getMovie();
        var theatre = show.getScreen().getTheatre();
        return new BookingResponse(b.getId(), b.getBookingCode(), b.getStatus(), show.getId(), movie.getId(),
                movie.getTitle(), movie.getKind(), movie.getLanguage(), movie.getCertificate(), movie.getPosterUrl(),
                theatre.getName(), theatre.getAddress(), theatre.getCity().getName(), show.getScreen().getName(),
                show.getStartTime(), labels, b.getSeatCount(), b.getTotalAmount(), b.getRefundAmount(),
                b.getHoldExpiresAt(), b.getCreatedAt(), b.getConfirmedAt(), b.getCancelledAt(), cancellable);
    }
}
