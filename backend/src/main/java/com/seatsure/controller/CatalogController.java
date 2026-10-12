package com.seatsure.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seatsure.dto.CityResponse;
import com.seatsure.dto.MovieResponse;
import com.seatsure.dto.SeatMapResponse;
import com.seatsure.dto.ShowDetailResponse;
import com.seatsure.dto.TheatreShowsResponse;
import com.seatsure.entity.MovieKind;
import com.seatsure.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Browse")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/cities")
    public List<CityResponse> cities() {
        return catalogService.cities();
    }

    @GetMapping("/movies")
    @Operation(summary = "Movies and events playing in a city, optionally on one date")
    public List<MovieResponse> playing(@RequestParam Long cityId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) MovieKind kind) {
        return catalogService.playing(cityId, date, kind);
    }

    @GetMapping("/movies/{id}")
    public MovieResponse movie(@PathVariable Long id) {
        return catalogService.movie(id);
    }

    @GetMapping("/movies/{id}/dates")
    @Operation(summary = "Dates that have shows for this movie in the city")
    public List<LocalDate> dates(@PathVariable Long id, @RequestParam Long cityId) {
        return catalogService.showDates(id, cityId);
    }

    @GetMapping("/movies/{id}/shows")
    @Operation(summary = "Shows of a movie on a date, grouped by theatre")
    public List<TheatreShowsResponse> shows(@PathVariable Long id, @RequestParam Long cityId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return catalogService.showsForMovie(id, cityId, date);
    }

    @GetMapping("/shows/{id}")
    public ShowDetailResponse show(@PathVariable Long id) {
        return catalogService.show(id);
    }

    @GetMapping("/shows/{id}/seats")
    @Operation(summary = "Seat map with the live status of every seat")
    public SeatMapResponse seats(@PathVariable Long id) {
        return catalogService.seatMap(id);
    }
}
