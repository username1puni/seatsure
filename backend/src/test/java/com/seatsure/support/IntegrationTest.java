package com.seatsure.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for tests that need the real database. One MySQL 8 container is started for the
 * whole test run (Testcontainers needs Docker) and every test starts from empty tables.
 */
@SpringBootTest(properties = {
        "app.seed.demo-data=false",
        // the job is called by hand in tests, so it doesn't fire in the middle of one
        "app.booking.expiry-check-ms=3600000",
        // enough connections for every thread in the concurrency tests to hit MySQL at once
        "spring.datasource.hikari.maximum-pool-size=30",
        "app.admin.email=admin@test.local",
        "app.admin.password=admin-password"
})
@Import(TestData.class)
public abstract class IntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("seatsure")
            .withUsername("seatsure")
            .withPassword("seatsure");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected TestData data;

    @BeforeEach
    void cleanDatabase() {
        data.clean();
    }
}
