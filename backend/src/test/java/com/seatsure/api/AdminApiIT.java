package com.seatsure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsure.dto.HoldRequest;
import com.seatsure.dto.MockCheckoutRequest;
import com.seatsure.dto.VerifyPaymentRequest;
import com.seatsure.service.BookingService;
import com.seatsure.service.PaymentService;
import com.seatsure.support.IntegrationTest;
import com.seatsure.support.TestData.ShowFixture;

@AutoConfigureMockMvc
class AdminApiIT extends IntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private Clock clock;

    private String admin;

    @BeforeEach
    void login() throws Exception {
        admin = call(post("/api/auth/login"), Map.of("email", "admin@test.local", "password", "admin-password"), 200)
                .get("token").asText();
    }

    @Test
    void adminSetsUpATheatreAndSchedulesShows() throws Exception {
        long city = call(auth(post("/api/admin/cities")), Map.of("name", "Mangaluru", "state", "Karnataka"), 201).get("id").asLong();
        long theatre = call(auth(post("/api/admin/theatres")),
                Map.of("cityId", city, "name", "Coastal Cinemas", "address", "MG Road"), 201).get("id").asLong();
        JsonNode screen = call(auth(post("/api/admin/screens")), Map.of("theatreId", theatre, "name", "Audi 1",
                "rows", 6, "seatsPerRow", 8, "premiumRows", List.of("E", "F")), 201);
        assertThat(screen.get("seatCount").asInt()).isEqualTo(48);
        assertThat(screen.get("premiumRows").toString()).isEqualTo("[\"E\",\"F\"]");

        long movie = call(auth(post("/api/admin/movies")), Map.of("title", "Admin Movie", "kind", "MOVIE",
                "language", "Tulu", "genre", "Drama", "durationMinutes", 120, "certificate", "U"), 201).get("id").asLong();
        LocalDateTime start = LocalDateTime.now(clock).plusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0);
        JsonNode show = call(auth(post("/api/admin/shows")), Map.of("movieId", movie, "screenId", screen.get("id").asLong(),
                "startTime", start.toString(), "priceNormal", 180, "pricePremium", 300), 201);
        assertThat(show.get("capacity").asInt()).isEqualTo(48);
        assertThat(LocalDateTime.parse(show.get("endTime").asText())).isEqualTo(start.plusMinutes(120));

        // 2 hours of film + 15 min cleaning: a show at 20:00 on the same screen clashes, 20:15 doesn't
        call(auth(post("/api/admin/shows")), Map.of("movieId", movie, "screenId", screen.get("id").asLong(),
                "startTime", start.plusHours(2).toString(), "priceNormal", 180, "pricePremium", 300), 409);
        call(auth(post("/api/admin/shows")), Map.of("movieId", movie, "screenId", screen.get("id").asLong(),
                "startTime", start.plusMinutes(135).toString(), "priceNormal", 180, "pricePremium", 300), 201);
    }

    @Test
    void pricingUpdateWithAnOldVersionIsRejected() throws Exception {
        ShowFixture show = data.show(48);
        JsonNode first = call(auth(put("/api/admin/shows/{id}/pricing", show.showId())),
                Map.of("priceNormal", 220, "pricePremium", 380, "version", 0), 200);
        assertThat(first.get("version").asLong()).isEqualTo(1);
        // a second admin still has the page open with version 0
        call(auth(put("/api/admin/shows/{id}/pricing", show.showId())),
                Map.of("priceNormal", 150, "pricePremium", 250, "version", 0), 409);
    }

    @Test
    void cancellingAShowRefundsEveryoneAndDashboardAddsUp() throws Exception {
        ShowFixture show = data.show(48);
        Long user = data.user("Fan");
        var booking = bookingService.holdSeats(user, new HoldRequest(show.showId(), show.row(4).subList(0, 2)));
        var order = paymentService.createOrder(user, booking.id());
        var paid = paymentService.mockCheckout(user, new MockCheckoutRequest(order.orderId(), true));
        paymentService.verify(user, new VerifyPaymentRequest(order.orderId(), paid.paymentId(), paid.signature()));

        JsonNode dashboard = call(auth(get("/api/admin/dashboard")), null, 200);
        assertThat(dashboard.get("totalRevenue").decimalValue()).isEqualByComparingTo("700");
        assertThat(dashboard.get("confirmedBookings").asInt()).isEqualTo(1);
        assertThat(dashboard.get("mostBookedShows").get(0).get("seatsSold").asInt()).isEqualTo(2);
        assertThat(dashboard.get("revenueByDay")).hasSize(7);

        // check-in at the gate works once
        String qr = data.ticketQr(booking.id());
        assertThat(call(auth(post("/api/admin/tickets/verify")), Map.of("code", qr), 200).get("result").asText()).isEqualTo("VALID");
        assertThat(call(auth(post("/api/admin/tickets/verify")), Map.of("code", qr), 200).get("result").asText()).isEqualTo("ALREADY_USED");
        assertThat(call(auth(post("/api/admin/tickets/verify")), Map.of("code", qr + "x"), 200).get("result").asText()).isEqualTo("INVALID");

        JsonNode cancelled = call(auth(post("/api/admin/shows/{id}/cancel", show.showId())), null, 200);
        assertThat(cancelled.get("bookingsRefunded").asInt()).isEqualTo(1);
        assertThat(data.count("select count(*) from payments where status = 'REFUNDED' and refund_amount = 700")).isEqualTo(1);
        assertThat(call(auth(get("/api/admin/dashboard")), null, 200).get("totalRevenue").decimalValue()).isEqualByComparingTo("0");
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + admin);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : json.readTree(response);
    }
}
