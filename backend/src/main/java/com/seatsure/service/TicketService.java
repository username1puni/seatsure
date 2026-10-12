package com.seatsure.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.seatsure.dto.TicketVerifyResponse;
import com.seatsure.entity.Booking;
import com.seatsure.entity.BookingSeat;
import com.seatsure.entity.BookingStatus;
import com.seatsure.entity.Seat;
import com.seatsure.exception.BadRequestException;
import com.seatsure.exception.NotFoundException;
import com.seatsure.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final String QR_PREFIX = "SEATSURE";

    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String newTicketToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Text inside the QR code: SEATSURE:<booking code>:<random token>. */
    public String qrText(Booking booking) {
        if (booking.getTicketToken() == null) {
            throw new BadRequestException("Tickets are only available for confirmed bookings");
        }
        return QR_PREFIX + ":" + booking.getBookingCode() + ":" + booking.getTicketToken();
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Tickets are only available for confirmed bookings");
        }
        return qrPng(qrText(booking), 320);
    }

    public byte[] qrPng(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Could not create QR code", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] ticketPdf(Long bookingId) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Tickets are only available for confirmed bookings");
        }
        var show = booking.getShow();
        var theatre = show.getScreen().getTheatre();
        List<String> seats = booking.getSeats().stream().map(BookingSeat::getSeat)
                .sorted(Seat.DISPLAY_ORDER).map(Seat::getLabel).toList();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Color brand = new Color(225, 29, 72);
        Font logo = new Font(Font.HELVETICA, 20, Font.BOLD, brand);
        Font title = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font label = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        Font value = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font small = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

        doc.add(new Paragraph("SeatSure", logo));
        doc.add(new Paragraph("E-ticket", small));
        Paragraph movie = new Paragraph(show.getMovie().getTitle(), title);
        movie.setSpacingBefore(14);
        doc.add(movie);
        doc.add(new Paragraph(show.getMovie().getLanguage() + "  |  " + show.getMovie().getCertificate(), small));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14);
        addCell(table, "THEATRE", theatre.getName() + ", " + theatre.getCity().getName(), label, value);
        addCell(table, "SCREEN", show.getScreen().getName(), label, value);
        addCell(table, "DATE AND TIME", show.getStartTime().format(SHOW_TIME), label, value);
        addCell(table, "SEATS (" + seats.size() + ")", String.join(", ", seats), label, value);
        addCell(table, "BOOKING ID", booking.getBookingCode(), label, value);
        addCell(table, "AMOUNT PAID", rupees(booking.getTotalAmount()), label, value);
        doc.add(table);

        Image qr = pdfImage(qrPng(qrText(booking), 360));
        qr.scaleAbsolute(150, 150);
        qr.setAlignment(Element.ALIGN_CENTER);
        qr.setSpacingBefore(10);
        doc.add(qr);
        Paragraph scan = new Paragraph("Show this QR code at the entrance", small);
        scan.setAlignment(Element.ALIGN_CENTER);
        doc.add(scan);

        Paragraph address = new Paragraph(theatre.getAddress(), small);
        address.setSpacingBefore(12);
        doc.add(address);
        doc.add(new Paragraph("Booked by " + booking.getUser().getFullName()
                + ". Cancellations: full refund up to 24 hours before the show, 75% up to 2 hours before.", small));
        doc.close();
        return out.toByteArray();
    }

    /** Entrance check. A ticket can be used once. */
    @Transactional
    public TicketVerifyResponse verify(String scanned) {
        String[] parts = scanned.trim().split(":");
        if (parts.length != 3 || !QR_PREFIX.equals(parts[0])) {
            return invalid("This is not a SeatSure ticket");
        }
        Booking booking = bookingRepository.findByBookingCode(parts[1]).orElse(null);
        if (booking == null || booking.getTicketToken() == null
                || !MessageDigest.isEqual(booking.getTicketToken().getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            return invalid("Ticket not found or has been tampered with");
        }
        var show = booking.getShow();
        List<String> seats = booking.getSeats().stream().map(BookingSeat::getSeat)
                .sorted(Seat.DISPLAY_ORDER).map(Seat::getLabel).toList();
        String result;
        String message;
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            result = "CANCELLED";
            message = "This booking was cancelled";
        } else if (booking.getCheckedInAt() != null) {
            result = "ALREADY_USED";
            message = "Already checked in at " + booking.getCheckedInAt();
        } else {
            booking.setCheckedInAt(Instant.now(clock));
            result = "VALID";
            message = "Valid ticket, let them in";
        }
        return new TicketVerifyResponse(result, message, booking.getBookingCode(), show.getMovie().getTitle(),
                show.getScreen().getTheatre().getName(), show.getStartTime(), seats, booking.getUser().getFullName());
    }

    private static TicketVerifyResponse invalid(String message) {
        return new TicketVerifyResponse("INVALID", message, null, null, null, null, List.of(), null);
    }

    private static void addCell(PdfPTable table, String name, String text, Font label, Font value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingBottom(8);
        cell.addElement(new Phrase(name, label));
        cell.addElement(new Phrase(text, value));
        table.addCell(cell);
    }

    private static Image pdfImage(byte[] png) {
        try {
            return Image.getInstance(png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String rupees(BigDecimal amount) {
        return "Rs. " + amount.setScale(2, RoundingMode.HALF_UP);
    }
}
