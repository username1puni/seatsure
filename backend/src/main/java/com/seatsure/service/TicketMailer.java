package com.seatsure.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.seatsure.entity.Booking;
import com.seatsure.repository.BookingRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Emails the PDF ticket after a booking is confirmed. Only runs when MAIL_HOST is set; without
 * SMTP the ticket is still available on the My Bookings page.
 */
@Slf4j
@Component
public class TicketMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final BookingRepository bookingRepository;
    private final TicketService ticketService;
    private final TransactionTemplate tx;
    private final String host;
    private final String from;

    public TicketMailer(ObjectProvider<JavaMailSender> mailSender, BookingRepository bookingRepository,
                        TicketService ticketService, TransactionTemplate tx,
                        @Value("${spring.mail.host:}") String host, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.bookingRepository = bookingRepository;
        this.ticketService = ticketService;
        this.tx = tx;
        this.host = host;
        this.from = from;
    }

    public boolean enabled() {
        return StringUtils.hasText(host) && mailSender.getIfAvailable() != null;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        if (!enabled()) {
            return;
        }
        try {
            byte[] pdf = ticketService.ticketPdf(event.bookingId());
            MimeMessage message = tx.execute(status -> {
                Booking booking = bookingRepository.findDetailed(event.bookingId()).orElseThrow();
                try {
                    MimeMessage mime = mailSender.getObject().createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mime, true);
                    helper.setFrom(from);
                    helper.setTo(booking.getUser().getEmail());
                    helper.setSubject("Your tickets for " + booking.getShow().getMovie().getTitle()
                            + " - " + booking.getBookingCode());
                    helper.setText("Hi " + booking.getUser().getFullName() + ",\n\nYour booking "
                            + booking.getBookingCode() + " is confirmed. The ticket with the entry QR code is attached."
                            + "\n\nEnjoy the show!\nSeatSure");
                    helper.addAttachment("ticket-" + booking.getBookingCode() + ".pdf", new ByteArrayResource(pdf));
                    return mime;
                } catch (jakarta.mail.MessagingException e) {
                    throw new IllegalStateException(e);
                }
            });
            mailSender.getObject().send(message);
            log.info("Ticket emailed for booking {}", event.bookingId());
        } catch (Exception e) {
            // the booking is already confirmed, a failed email must not undo it
            log.warn("Could not email ticket for booking {}", event.bookingId(), e);
        }
    }
}
