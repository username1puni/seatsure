package com.seatsure.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.seatsure.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Gives seats back when someone held them and never paid. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryJob {

    private static final int BATCH = 200;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.booking.expiry-check-ms}", initialDelay = 5000)
    public void run() {
        releaseExpiredHolds();
    }

    /** Returns how many holds were released. Each booking gets its own small transaction. */
    public int releaseExpiredHolds() {
        List<Long> ids = bookingRepository.findExpiredHoldIds(Instant.now(clock), PageRequest.of(0, BATCH));
        int released = 0;
        for (Long id : ids) {
            try {
                if (bookingService.expireHold(id)) {
                    released++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // the payment confirmed (or the user cancelled) this booking while we were looking at it
                log.info("Booking {} changed while expiring it, leaving it alone", id);
            }
        }
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
        return released;
    }
}
