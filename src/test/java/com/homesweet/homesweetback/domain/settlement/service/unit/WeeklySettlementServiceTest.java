package com.homesweet.homesweetback.domain.settlement.service.unit;

import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.SettlementCacheService;
import com.homesweet.homesweetback.domain.settlement.service.WeeklySettlementService;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import org.junit.jupiter.api.Disabled;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeeklySettlementService 단위 테스트")
@Disabled
class WeeklySettlementServiceTest {

    @InjectMocks
    private WeeklySettlementService weeklySettlementService;

    @Mock
    private WeeklySettlementRepository weeklySettlementRepository;

    @Mock
    private SettlementCacheService settlementCacheService;

    @Mock
    private WeeklyDateRangeCalculator weeklyCalc;

    @Mock
    private EmptyResponse emptyResponse;

    Long userId = 1L;
    Pageable pageable = PageRequest.of(0, 10);
    LocalDate start = LocalDate.of(2025, 11, 10);
    LocalDate end = LocalDate.of(2025, 11, 16);

    WeeklyDateRangeCalculator.WeeklyDateRange range =
            WeeklyDateRangeCalculator.getWeeklyDateRange(start, end);

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("캐시에 데이터가 있다면 Repository 조회 없이 바로 반환")
        void success_cacheHit() {

            WeeklySettlementResponse res = new WeeklySettlementResponse(
                    (short) 2025, (byte) 11, (byte) 3,
                    start, end,
                    null, null, null, null, null,
                    0.0, 10L
            );

            WeeklyDateRangeCalculator.WeeklyDateRange range =
                    new WeeklyDateRangeCalculator.WeeklyDateRange(
                            start,         // 2025-11-10
                            start.plusWeeks(1),
                            (byte) 3// 2025-11-17 (exclusive)
                    );
            // Range 내부 메서드 stub
            given(range.firstWeekStart()).willReturn(start);
            given(range.lastWeekStartEx()).willReturn(end);

            // 1) Cache Hit
            given(settlementCacheService.getWeeklyContentCache(
                    userId, start, end, pageable
            )).willReturn(List.of(res));

            // 2) Range 계산
//            given(weeklyCalc.getWeeklyDateRange(start, end))
//                    .willReturn(range);

            // 3) count 조회
            given(weeklySettlementRepository.countByRange(
                    userId, start, end
            )).willReturn(10L);

            // when
            Page<WeeklySettlementResponse> result =
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(10L);

            verify(settlementCacheService, times(1))
                    .getWeeklyContentCache(userId, start, end, pageable);

            verify(weeklySettlementRepository, times(1))
                    .countByRange(userId, start, end);
        }

        @Test
        @DisplayName("캐시 Miss → EmptyResponse 반환")
        void success_cacheMiss_emptyPage() {
            // 1) Cache Miss
            given(settlementCacheService.getWeeklyContentCache(
                    userId, start, end, pageable
            )).willReturn(Collections.emptyList());

            // 2) range 계산
            given(weeklyCalc.getWeeklyDateRange(start, end))
                    .willReturn(range);

            Page<WeeklySettlementResponse> emptyPage =
                    new PageImpl<>(List.of(), pageable, 0);

            // 3) empty response 반환
            given(emptyResponse.createEmptyWeekly(range, pageable))
                    .willReturn(emptyPage);

            // when
            Page<WeeklySettlementResponse> result =
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable);

            // then
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();

            verify(emptyResponse, times(1))
                    .createEmptyWeekly(range, pageable);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("Cache 호출 중 예외 발생 시 예외 전파")
        void cacheError() {

            given(settlementCacheService.getWeeklyContentCache(
                    userId, start, end, pageable
            )).willThrow(new RuntimeException("CACHE ERROR"));

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable)
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("CACHE ERROR");
        }

        @Test
        @DisplayName("count 조회 중 예외 발생 시 예외 전파")
        void countError() {

            WeeklySettlementResponse res = new WeeklySettlementResponse(
                    (short) 2025, (byte) 11, (byte) 46,
                    start, end,
                    null, null, null, null, null,
                    0.0, 10L
            );
            WeeklyDateRangeCalculator.WeeklyDateRange range = mock(WeeklyDateRangeCalculator.WeeklyDateRange.class);

            given(settlementCacheService.getWeeklyContentCache(
                    userId, start, end, pageable
            )).willReturn(List.of(res));

            given(weeklyCalc.getWeeklyDateRange(start, end))
                    .willReturn(range);

            given(weeklySettlementRepository.countByRange(
                    userId, range.firstWeekStart(), range.lastWeekStartEx()
            )).willThrow(new RuntimeException("DB ERROR"));

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable)
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB ERROR");
        }
    }
}
