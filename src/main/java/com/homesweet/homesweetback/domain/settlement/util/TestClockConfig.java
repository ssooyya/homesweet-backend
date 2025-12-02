package com.homesweet.homesweetback.domain.settlement.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Configuration
@Profile("test")
public class TestClockConfig {

    @Bean
    public Clock clock() {
        return Clock.fixed(
                LocalDateTime.of(2025, 11, 15, 0, 0)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                ZoneId.of("Asia/Seoul")
        );
    }
}

