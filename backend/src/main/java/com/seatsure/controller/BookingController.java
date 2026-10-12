package com.seatsure.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.HoldRequest;
import com.seatsure.dto.RefundPreview;
import com.seatsure.security.AuthUser;
import com.seatsure.service.BookingService;
import com.seatsure.service.TicketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;
    private final TicketService ticketService;

    @PostMapping("/hold")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hold seats for a few minutes while paying. 409 if any seat is taken.")
    public BookingResponse hold(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody HoldRequest request) {
        return bookingService.holdSeats(user.getId(), request);
    }

    @DeleteMapping("/{id}/hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Give the held seats back before paying")
    public void releaseHold(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        bookingService.cancelHoldForUser(user.getId(), id);
    }

    @GetMapping
    @Operation(summary = "Booking history of the logged in user")
    public List<BookingResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return bookingService.myBookings(user.getId());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookingService.get(user.getId(), user.isAdmin(), id);
    }

    @GetMapping("/{id}/refund-preview")
    @Operation(summary = "How much would come back if this booking is cancelled now")
    public RefundPreview refundPreview(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookingService.refundPreview(user.getId(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a confirmed booking and refund as per the policy")
    public BookingResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookingService.cancelByUser(user.getId(), id);
    }

    @GetMapping(value = "/{id}/ticket.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the ticket as a PDF with the entry QR code")
    public ResponseEntity<byte[]> ticketPdf(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        bookingService.checkAccess(user.getId(), user.isAdmin(), id);
        byte[] pdf = ticketService.ticketPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=seatsure-ticket-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "The entry QR code as an image")
    public ResponseEntity<byte[]> qr(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        bookingService.checkAccess(user.getId(), user.isAdmin(), id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(ticketService.qrPng(id));
    }
}
