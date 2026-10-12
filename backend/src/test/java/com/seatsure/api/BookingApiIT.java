package com.seatsure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsure.support.IntegrationTest;
import com.seatsure.support.TestData.ShowFixture;

/** The whole user journey over HTTP: sign up, browse, hold, pay, ticket, cancel. */
@AutoConfigureMockMvc
class BookingApiIT extends IntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void userBooksPaysDownloadsTicketAndCancels() throws Exception {
        ShowFixture show = data.show(72);
        String token = register("Meera", "meera@test.local");

        mvc.perform(get("/api/movies").param("cityId", show.cityId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Movie"));
        mvc.perform(get("/api/shows/{id}/seats", show.showId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(5))
                .andExpect(jsonPath("$.rows[4].category").value("PREMIUM"));

        JsonNode hold = call(auth(post("/api/bookings/hold"), token),
                Map.of("showId", show.showId(), "seatIds", show.row(2).subList(4, 6)), 201);
        assertThat(hold.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(hold.get("seats").toString()).isEqualTo("[\"C5\",\"C6\"]");
        long bookingId = hold.get("id").asLong();

        JsonNode order = call(auth(post("/api/payments/order"), token), Map.of("bookingId", bookingId), 200);
        assertThat(order.get("gateway").asText()).isEqualTo("MOCK");
        assertThat(order.get("amountPaise").asLong()).isEqualTo(40000);
        JsonNode paid = call(auth(post("/api/payments/mock/checkout"), token),
                Map.of("orderId", order.get("orderId").asText(), "success", true), 200);
        JsonNode confirmed = call(auth(post("/api/payments/verify"), token), Map.of(
                "orderId", order.get("orderId").asText(),
                "paymentId", paid.get("paymentId").asText(),
                "signature", paid.get("signature").asText()), 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

        mvc.perform(auth(get("/api/bookings/{id}/ticket.pdf", bookingId), token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(auth(get("/api/bookings"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingCode").value(hold.get("bookingCode").asText()));

        JsonNode preview = call(auth(get("/api/bookings/{id}/refund-preview", bookingId), token), null, 200);
        assertThat(preview.get("refundPercent").asInt()).isEqualTo(100);
        JsonNode cancelled = call(auth(post("/api/bookings/{id}/cancel", bookingId), token), null, 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("refundAmount").decimalValue()).isEqualByComparingTo("400");

        // the seats are free again for the next person
        mvc.perform(get("/api/shows/{id}/seats", show.showId()))
                .andExpect(jsonPath("$.rows[2].seats[4].status").value("AVAILABLE"));
    }

    @Test
    void secondUserGets409ForATakenSeat() throws Exception {
        ShowFixture show = data.show(72);
        String first = register("First", "first@test.local");
        String second = register("Second", "second@test.local");
        Map<String, Object> body = Map.of("showId", show.showId(), "seatIds", List.of(show.row(0).get(0)));

        call(auth(post("/api/bookings/hold"), first), body, 201);
        mvc.perform(auth(post("/api/bookings/hold"), second).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("A1")));
    }

    @Test
    void userCanReleaseTheirHold() throws Exception {
        ShowFixture show = data.show(72);
        String token = register("Changer", "changer@test.local");
        JsonNode hold = call(auth(post("/api/bookings/hold"), token),
                Map.of("showId", show.showId(), "seatIds", show.row(1).subList(0, 2)), 201);

        mvc.perform(auth(delete("/api/bookings/{id}/hold", hold.get("id").asLong()), token))
                .andExpect(status().isNoContent());
        assertThat(data.count("select count(*) from show_seats where status = 'HELD'")).isZero();
    }

    @Test
    void cannotSeeOrCancelSomeoneElsesBooking() throws Exception {
        ShowFixture show = data.show(72);
        String owner = register("Owner", "owner@test.local");
        String other = register("Other", "other@test.local");
        JsonNode hold = call(auth(post("/api/bookings/hold"), owner),
                Map.of("showId", show.showId(), "seatIds", List.of(show.row(0).get(3))), 201);

        mvc.perform(auth(get("/api/bookings/{id}", hold.get("id").asLong()), other)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/bookings/{id}/hold", hold.get("id").asLong()), other)).andExpect(status().isForbidden());
    }

    @Test
    void securityRules() throws Exception {
        String token = register("Plain User", "plain@test.local");
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/admin/dashboard"), token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cities")).andExpect(status().isOk());
        mvc.perform(get("/api/payments/config")).andExpect(jsonPath("$.gateway").value("MOCK"));
    }

    @Test
    void validationErrorsAreReadable() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Enter a valid email"))
                .andExpect(jsonPath("$.fields.password").exists());
        register("Taken", "taken@test.local");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Again\",\"email\":\"TAKEN@test.local\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    private String register(String name, String email) throws Exception {
        JsonNode res = call(post("/api/auth/register"),
                Map.of("fullName", name, "email", email, "password", "password123"), 201);
        return res.get("token").asText();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
