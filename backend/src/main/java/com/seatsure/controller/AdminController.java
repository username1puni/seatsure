package com.seatsure.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatsure.dto.AdminShowResponse;
import com.seatsure.dto.CityRequest;
import com.seatsure.dto.CityResponse;
import com.seatsure.dto.DashboardResponse;
import com.seatsure.dto.MovieRequest;
import com.seatsure.dto.MovieResponse;
import com.seatsure.dto.PricingRequest;
import com.seatsure.dto.ScreenRequest;
import com.seatsure.dto.ScreenResponse;
import com.seatsure.dto.ShowRequest;
import com.seatsure.dto.TheatreRequest;
import com.seatsure.dto.TheatreResponse;
import com.seatsure.dto.TicketVerifyRequest;
import com.seatsure.dto.TicketVerifyResponse;
import com.seatsure.service.TicketService;
import com.seatsure.service.admin.AdminCatalogService;
import com.seatsure.service.admin.DashboardService;
import com.seatsure.service.admin.ShowAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminController {

    private final AdminCatalogService catalogService;
    private final ShowAdminService showService;
    private final DashboardService dashboardService;
    private final TicketService ticketService;

    @GetMapping("/dashboard")
    @Operation(summary = "Revenue, bookings and the most booked shows")
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityRequest request) {
        return catalogService.createCity(request);
    }

    @GetMapping("/theatres")
    public List<TheatreResponse> theatres(@RequestParam(required = false) Long cityId) {
        return catalogService.theatres(cityId);
    }

    @PostMapping("/theatres")
    @ResponseStatus(HttpStatus.CREATED)
    public TheatreResponse createTheatre(@Valid @RequestBody TheatreRequest request) {
        return catalogService.createTheatre(request);
    }

    @PutMapping("/theatres/{id}")
    public TheatreResponse updateTheatre(@PathVariable Long id, @Valid @RequestBody TheatreRequest request) {
        return catalogService.updateTheatre(id, request);
    }

    @GetMapping("/theatres/{id}/screens")
    public List<ScreenResponse> screens(@PathVariable Long id) {
        return catalogService.screens(id);
    }

    @GetMapping("/screens/{id}")
    public ScreenResponse screen(@PathVariable Long id) {
        return catalogService.screen(id);
    }

    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a screen and generate its seat layout")
    public ScreenResponse createScreen(@Valid @RequestBody ScreenRequest request) {
        return catalogService.createScreen(request);
    }

    @DeleteMapping("/screens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScreen(@PathVariable Long id) {
        catalogService.deleteScreen(id);
    }

    @GetMapping("/movies")
    public List<MovieResponse> movies() {
        return catalogService.movies();
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) {
        return catalogService.createMovie(request);
    }

    @PutMapping("/movies/{id}")
    public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return catalogService.updateMovie(id, request);
    }

    @GetMapping("/shows")
    public List<AdminShowResponse> shows(@RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showService.shows(date);
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a show. Rejects overlapping shows on the same screen.")
    public AdminShowResponse createShow(@Valid @RequestBody ShowRequest request) {
        return showService.create(request);
    }

    @PutMapping("/shows/{id}/pricing")
    @Operation(summary = "Change normal and premium prices for future bookings")
    public AdminShowResponse updatePricing(@PathVariable Long id, @Valid @RequestBody PricingRequest request) {
        return showService.updatePricing(id, request);
    }

    @PostMapping("/shows/{id}/cancel")
    @Operation(summary = "Cancel a show and refund everyone in full")
    public Map<String, Integer> cancelShow(@PathVariable Long id) {
        return Map.of("bookingsRefunded", showService.cancel(id));
    }

    @PostMapping("/tickets/verify")
    @Operation(summary = "Check a scanned ticket QR at the entrance")
    public TicketVerifyResponse verifyTicket(@Valid @RequestBody TicketVerifyRequest request) {
        return ticketService.verify(request.code());
    }
}
