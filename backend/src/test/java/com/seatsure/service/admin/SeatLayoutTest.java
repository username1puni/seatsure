package com.seatsure.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seatsure.exception.BadRequestException;

class SeatLayoutTest {

    @Test
    void rowsAreLettersFromTheFront() {
        assertThat(AdminCatalogService.rowLabel(0)).isEqualTo("A");
        assertThat(AdminCatalogService.rowLabel(25)).isEqualTo("Z");
    }

    @Test
    void lastTwoRowsArePremiumByDefault() {
        assertThat(AdminCatalogService.premiumRows(null, 10)).containsExactly("I", "J");
        assertThat(AdminCatalogService.premiumRows(null, 1)).containsExactly("A");
    }

    @Test
    void premiumRowsCanBeChosen() {
        assertThat(AdminCatalogService.premiumRows(List.of("e", " F "), 8)).containsExactly("E", "F");
        assertThat(AdminCatalogService.premiumRows(List.of(), 8)).isEmpty();
    }

    @Test
    void premiumRowOutsideTheScreenIsRejected() {
        assertThatThrownBy(() -> AdminCatalogService.premiumRows(List.of("K"), 5)).isInstanceOf(BadRequestException.class);
    }
}
