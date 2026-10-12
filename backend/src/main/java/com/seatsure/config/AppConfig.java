package com.seatsure.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableAsync
public class AppConfig {

    /** Show times are stored as theatre local time, so "now" has to be in the same zone. */
    @Bean
    Clock clock(@Value("${app.timezone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
