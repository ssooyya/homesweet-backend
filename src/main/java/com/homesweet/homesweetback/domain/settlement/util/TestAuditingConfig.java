package com.homesweet.homesweetback.domain.settlement.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@Profile("test")             // 테스트 환경에서만 활성화됨
public class TestAuditingConfig {

    @Bean
    public DateTimeProvider dateTimeProvider() {
        // 테스트에 필요한 orderedAt 날짜 강제 지정
        return () -> Optional.of(
                LocalDateTime.of(2025, 11, 14, 10, 0)
        );
    }
}
