CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    phone         VARCHAR(15),
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE cities (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(60) NOT NULL,
    state VARCHAR(60) NOT NULL,
    CONSTRAINT uk_cities_name UNIQUE (name)
);

CREATE TABLE theatres (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    city_id    BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_theatres_city FOREIGN KEY (city_id) REFERENCES cities (id)
);

CREATE TABLE screens (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    theatre_id    BIGINT      NOT NULL,
    name          VARCHAR(50) NOT NULL,
    total_rows    INT         NOT NULL,
    seats_per_row INT         NOT NULL,
    CONSTRAINT fk_screens_theatre FOREIGN KEY (theatre_id) REFERENCES theatres (id),
    CONSTRAINT uk_screens_theatre_name UNIQUE (theatre_id, name)
);

CREATE TABLE seats (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    screen_id   BIGINT      NOT NULL,
    row_label   VARCHAR(2)  NOT NULL,
    seat_number INT         NOT NULL,
    category    VARCHAR(10) NOT NULL,
    CONSTRAINT fk_seats_screen FOREIGN KEY (screen_id) REFERENCES screens (id),
    CONSTRAINT uk_seats_position UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE movies (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(150) NOT NULL,
    description      TEXT,
    kind             VARCHAR(10)  NOT NULL,
    language         VARCHAR(30)  NOT NULL,
    genre            VARCHAR(60)  NOT NULL,
    duration_minutes INT          NOT NULL,
    certificate      VARCHAR(5)   NOT NULL,
    poster_url       VARCHAR(500),
    release_date     DATE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE shows (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id      BIGINT        NOT NULL,
    screen_id     BIGINT        NOT NULL,
    start_time    DATETIME      NOT NULL,
    end_time      DATETIME      NOT NULL,
    price_normal  DECIMAL(10, 2) NOT NULL,
    price_premium DECIMAL(10, 2) NOT NULL,
    status        VARCHAR(12)   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_shows_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_shows_screen FOREIGN KEY (screen_id) REFERENCES screens (id)
);
CREATE INDEX idx_shows_screen_time ON shows (screen_id, start_time);
CREATE INDEX idx_shows_movie_time ON shows (movie_id, start_time);

CREATE TABLE bookings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code    VARCHAR(12)    NOT NULL,
    user_id         BIGINT         NOT NULL,
    show_id         BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    seat_count      INT            NOT NULL,
    total_amount    DECIMAL(10, 2) NOT NULL,
    refund_amount   DECIMAL(10, 2),
    hold_expires_at DATETIME(6)    NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    confirmed_at    DATETIME(6),
    cancelled_at    DATETIME(6),
    checked_in_at   DATETIME(6),
    ticket_token    VARCHAR(40),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_bookings_code UNIQUE (booking_code),
    CONSTRAINT uk_bookings_ticket UNIQUE (ticket_token),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows (id)
);
CREATE INDEX idx_bookings_status_expiry ON bookings (status, hold_expires_at);
CREATE INDEX idx_bookings_user ON bookings (user_id, created_at);

-- One row per seat per show. This is what the seat map reads and what gets locked during a hold.
CREATE TABLE show_seats (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id    BIGINT      NOT NULL,
    seat_id    BIGINT      NOT NULL,
    status     VARCHAR(10) NOT NULL,
    booking_id BIGINT,
    held_until DATETIME(6),
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_show_seats_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_show_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT fk_show_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_show_seats UNIQUE (show_id, seat_id)
);

-- Seats owned by a booking. MySQL has no partial unique index, so active_flag is 1 only for
-- HELD / CONFIRMED rows and NULL otherwise. NULLs never clash in a unique key, so a seat can
-- have any number of old cancelled rows but only one live booking.
CREATE TABLE booking_seats (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id  BIGINT         NOT NULL,
    show_id     BIGINT         NOT NULL,
    seat_id     BIGINT         NOT NULL,
    price       DECIMAL(10, 2) NOT NULL,
    status      VARCHAR(10)    NOT NULL,
    active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('HELD', 'CONFIRMED') THEN 1 ELSE NULL END) STORED,
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT uk_booking_seats_active UNIQUE (show_id, seat_id, active_flag)
);

CREATE TABLE payments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id         BIGINT         NOT NULL,
    gateway            VARCHAR(10)    NOT NULL,
    gateway_order_id   VARCHAR(60)    NOT NULL,
    gateway_payment_id VARCHAR(60),
    amount             DECIMAL(10, 2) NOT NULL,
    status             VARCHAR(10)    NOT NULL,
    refund_id          VARCHAR(60),
    refund_amount      DECIMAL(10, 2),
    failure_reason     VARCHAR(255),
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_payments_order UNIQUE (gateway_order_id)
);
