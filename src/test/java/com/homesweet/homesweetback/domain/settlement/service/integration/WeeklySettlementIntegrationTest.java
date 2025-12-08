package com.homesweet.homesweetback.domain.settlement.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.homesweet.homesweetback.HomesweetBackApplication;
import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.grade.entity.Grade;
import com.homesweet.homesweetback.domain.grade.repository.GradeRepository;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.product.product.repository.jpa.entity.SkuEntity;
import com.homesweet.homesweetback.domain.settlement.data.HelpIntegrationData;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.DailySettlementService;
import com.homesweet.homesweetback.domain.settlement.service.SettlementCacheService;
import com.homesweet.homesweetback.domain.settlement.service.SettlementService;
import com.homesweet.homesweetback.domain.settlement.service.WeeklySettlementService;
import com.homesweet.homesweetback.domain.settlement.util.SettlementKeyBuilder;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest()
@ActiveProfiles("test")
@DisplayName("WeeklyService 통합 테스트")
@Disabled
public class WeeklySettlementIntegrationTest {
    @Autowired
    EntityManager em;
    @Autowired
    private WeeklySettlementService weeklySettlementService;

    @Autowired
    private WeeklySettlementRepository weeklySettlementRepository;
    @Autowired
    private SettlementCacheService settlementCacheService;

    @Autowired
    private WeeklyDateRangeCalculator weeklyCalc;
    @Autowired
    private SettlementKeyBuilder settlementKeyBuilder;

    @Autowired
    private EmptyResponse emptyResponse;
    Pageable pageable = PageRequest.of(0, 10);
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    @BeforeEach
    void setupRedis() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        redisTemplate.setValueSerializer(serializer);
    }

    @BeforeEach
    void setup() {
        // 캐시 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // DB 초기화
        weeklySettlementRepository.deleteAll();
    }
    Long userId = 1L;
    LocalDate start = LocalDate.of(2025, 11, 10);
    LocalDate end = LocalDate.of(2025, 11, 10);

    @Nested
    @DisplayName("성공 케이스")
    class Success{
        @Test
        @DisplayName("1) 캐시 HIT → DB count 조회 후 정상 Page 반환")
        void cacheHit_success() {

            // given
            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 11, 10);
            LocalDate end = LocalDate.of(2025, 11, 16);

            // Redis 캐시 초기화(MISS 방지용)
            redisTemplate.getConnectionFactory().getConnection().flushAll();

            // ---- 1) 캐시에 넣을 응답 생성 ----
            WeeklySettlementResponse cached = new WeeklySettlementResponse(
                    (short) 2025, (byte) 11, (byte) 2,
                    start, end,
                    BigDecimal.valueOf(60000),
                    BigDecimal.valueOf(3000),
                    BigDecimal.valueOf(6000),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(51000),
                    100.0,
                    1L
            );

            // ---- 2) 캐시 key 생성 ----
            String key = settlementKeyBuilder.weeklySummaryKey(
                    userId, start, pageable.getPageNumber(), pageable.getPageSize()
            );

            // ---- 3) Redis 캐시에 주입 ----
            redisTemplate.opsForValue().set(key, List.of(cached));

            // ---- 4) DB에 실제 데이터 넣어서 count가 1이 되게 함 ----
            weeklySettlementRepository.save(
                    WeeklySettlement.builder()
                            .userId(userId)
                            .year((short) 2025)
                            .month((byte) 11)
                            .totalSales(BigDecimal.valueOf(60000))
                            .build()
            );

            // when
            Page<WeeklySettlementResponse> result =
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).week()).isEqualTo((byte) 2);
            assertThat(result.getTotalElements()).isEqualTo(1L);
        }

        @Test
        @DisplayName("2) 캐시 MISS → EmptyResponse 반환")
        void cacheMiss_emptyResponse() {

            // given
            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 11, 10);
            LocalDate end = LocalDate.of(2025, 11, 16);

            redisTemplate.getConnectionFactory().getConnection().flushAll(); // 캐시 완전 초기화

            // when
            Page<WeeklySettlementResponse> result =
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Fail {
        @Test
        @DisplayName("3) DB count 에러 발생 → 예외 전파 확인")
        void countError() {

            // given
            Long userId = 99L;
            LocalDate start = LocalDate.of(2025, 11, 10);
            LocalDate end = LocalDate.of(2025, 11, 16);

            redisTemplate.getConnectionFactory().getConnection().flushAll();

            // 캐시 HIT 만들기
            WeeklySettlementResponse cached = new WeeklySettlementResponse(
                    (short) 2025, (byte) 11, (byte) 2,
                    start, end,
                    BigDecimal.valueOf(10000),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(10000),
                    100.0,
                    1L
            );

            String key = settlementKeyBuilder.weeklySummaryKey(
                    userId, start, pageable.getPageNumber(), pageable.getPageSize()
            );
            redisTemplate.opsForValue().set(key, List.of(cached));

            // 일부러 잘못된 파라미터로 DB count 오류 유도
            WeeklySettlement fake = WeeklySettlement.builder()
                    .userId(userId)
                    .year((short) 2025)
                    .month((byte) 11)
                    .build();
            weeklySettlementRepository.save(fake);

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySummary(userId, start, end.plusDays(999), pageable)
            ).isInstanceOf(Exception.class);
        }


    }
}