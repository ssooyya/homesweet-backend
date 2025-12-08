package com.homesweet.homesweetback.domain.settlement.service.unit;

import com.homesweet.homesweetback.domain.settlement.dto.response.DailySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.DailySettlementService;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.service.SettlementCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("일별 집계 서비스 테스트")
class DailySettlementServiceTest {

    @InjectMocks
    private DailySettlementService dailySettlementService;
    @Mock
    private DailySettlementRepository dailySettlementRepository;
    @Mock
    private EmptyResponse emptyResponse;

    @Mock
    private SettlementCacheService settlementCacheService;
    Pageable pageable = PageRequest.of(0, 10);

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("캐시 HIT → count 조회 후 정상 Page 반환")
        void getDailySummary_success() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 3, 1);
            LocalDate end   = LocalDate.of(2025, 3, 1);

            DailySettlementResponse resp = new DailySettlementResponse(
                    BigDecimal.valueOf(10000),
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(500),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(8500),
                    start,
                    "COMPLETED",
                    100.0,
                    1L
            );

            // 1) 캐시 HIT 설정
            when(settlementCacheService.getDailyContentCache(userId, start, end, pageable))
                    .thenReturn(List.of(resp));

            // 2) count 조회 Stub
            LocalDateTime from = start.atStartOfDay();
            LocalDateTime to = end.plusDays(1).atStartOfDay();

            when(dailySettlementRepository.countByDailySettlementRange(userId, from, to))
                    .thenReturn(1L);

            // WHEN
            Page<DailySettlementResponse> result =
                    dailySettlementService.getDailySummary(userId, start, end, pageable);

            // THEN
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1L);
            assertThat(result.getContent().get(0).totalSales())
                    .isEqualByComparingTo("10000");

            // EmptyResponse 호출 X
            verify(emptyResponse, never()).createEmptyDaily(any(), any());
        }
    }
    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("캐시 MISS → EmptyResponse 반환, count 조회되지 않음")
        void getDailySummary_empty() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 3, 1);
            LocalDate end   = LocalDate.of(2025, 3, 5);

            // 1) 캐시 MISS 설정
            when(settlementCacheService.getDailyContentCache(userId, start, end, pageable))
                    .thenReturn(List.of());

            Page<DailySettlementResponse> emptyPage =
                    new PageImpl<>(List.of(), pageable, 0);

            when(emptyResponse.createEmptyDaily(start, pageable))
                    .thenReturn(emptyPage);

            // WHEN
            Page<DailySettlementResponse> result =
                    dailySettlementService.getDailySummary(userId, start, end, pageable);

            // THEN
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);

            // EmptyResponse 호출 O
            verify(emptyResponse, times(1))
                    .createEmptyDaily(start, pageable);

            // count 조회 금지
            verify(dailySettlementRepository, never())
                    .countByDailySettlementRange(any(), any(), any());
        }

        @Test
        @DisplayName("캐시 HIT 상태에서 count 조회 실패 시 예외 전파")
        void getDailySummary_countError() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 4, 1);
            LocalDate end   = LocalDate.of(2025, 4, 2);

            DailySettlementResponse resp = new DailySettlementResponse(
                    BigDecimal.valueOf(10000),
                    BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(500),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(8500),
                    start,
                    "COMPLETED",
                    90.0,
                    1L
            );

            // 캐시 HIT
            when(settlementCacheService.getDailyContentCache(userId, start, end, pageable))
                    .thenReturn(List.of(resp));

            // count 예외 유도
            when(dailySettlementRepository.countByDailySettlementRange(any(), any(), any()))
                    .thenThrow(new RuntimeException("DB ERROR"));

            // WHEN & THEN
            assertThatThrownBy(() ->
                    dailySettlementService.getDailySummary(userId, start, end, pageable)
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB ERROR");

            verify(emptyResponse, never()).createEmptyDaily(any(), any());
        }
    }
}
