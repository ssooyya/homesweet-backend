package com.homesweet.homesweetback.domain.settlement.mapper;

import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.dto.response.*;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyGrowthCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("매핑 테스트")
class SettlementMapperTest {
    @InjectMocks
    private SettlementMapper settlementMapper;

    @Mock
    private MonthlyGrowthCalculator monthlyGrowthCalculator;

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("DailyResponse 매핑 성공")
        void toDailySettlementResponse() {

            DailySettlement d = HelperData.getDailySettlement();
            SettlementStatsDto stats = new SettlementStatsDto(10L, 10L); // 100% 완료

            DailySettlementResponse resp =
                    settlementMapper.toDailySettlementResponse(d, stats);

            assertThat(resp.totalSales()).isEqualTo(BigDecimal.valueOf(1500000));
            assertThat(resp.totalFee()).isEqualTo(BigDecimal.valueOf(75000));
            assertThat(resp.totalVat()).isEqualTo(BigDecimal.valueOf(150000));
            assertThat(resp.totalSettlement()).isEqualTo(BigDecimal.valueOf(1575000));
            assertThat(resp.settlementStatus()).isEqualTo("COMPLETED");  // 100% → COMPLETED
            assertThat(resp.completedRate()).isEqualTo(100.0);
            assertThat(resp.totalCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("DailySettlementResponse 리스트 매핑 성공")
        void toDailySettlementResponseList() {

            List<DailySettlement> list = List.of(
                    HelperData.getDailySettlement(),
                    HelperData.getDailySettlement()
            );

            SettlementStatsDto stats = new SettlementStatsDto(10L, 10L);

            List<DailySettlementResponse> result =
                    settlementMapper.toDailySettlementResponseList(list, stats);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).settlementStatus()).isEqualTo("COMPLETED");
            assertThat(result.get(0).totalCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("WeeklySettlement → WeeklySettlementResponse 매핑 성공")
        void toWeeklySettlementResponse_success() {

            WeeklySettlement w = WeeklySettlement.builder()
                    .year((short) 2025)
                    .month((byte) 11)
                    .weekStartDate(LocalDate.of(2025, 11, 10))
                    .weekEndDate(LocalDate.of(2025, 11, 16))
                    .totalSales(BigDecimal.valueOf(100000))
                    .totalFee(BigDecimal.valueOf(5000))
                    .totalVat(BigDecimal.valueOf(10000))
                    .totalRefund(BigDecimal.ZERO)
                    .totalSettlement(BigDecimal.valueOf(85000))
                    .build();

            SettlementStatsDto stats = new SettlementStatsDto(10L, 5L); // 완료율 50%

            List<WeeklySettlementResponse> responses =
                    settlementMapper.toWeeklySettlementResponse(List.of(w), stats, (byte) 2);

            WeeklySettlementResponse r = responses.get(0);

            assertThat(r.year()).isEqualTo((short) 2025);
            assertThat(r.month()).isEqualTo((byte) 11);
            assertThat(r.week()).isEqualTo((byte) 2);
            assertThat(r.totalSales()).isEqualTo(BigDecimal.valueOf(100000));
            assertThat(r.totalSettlement()).isEqualTo(BigDecimal.valueOf(85000));
            assertThat(r.completedRate()).isEqualTo(50.0);  // FIXED
            assertThat(r.totalCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("MonthlySettlement 매핑 + 성장률 계산 성공")
        void toMonthlyResponses_success() {

            MonthlySettlement m1 = MonthlySettlement.builder()
                    .year((short) 2025).month((byte) 1)
                    .totalSales(BigDecimal.valueOf(100)).build();

            MonthlySettlement m2 = MonthlySettlement.builder()
                    .year((short) 2025).month((byte) 2)
                    .totalSales(BigDecimal.valueOf(200)).build();

            MonthlySettlement m3 = MonthlySettlement.builder()
                    .year((short) 2025).month((byte) 3)
                    .totalSales(BigDecimal.valueOf(300)).build();

            List<MonthlySettlement> list = List.of(m1, m2, m3);

            when(monthlyGrowthCalculator.growthCalculate(null, BigDecimal.valueOf(100)))
                    .thenReturn(0.0);
            when(monthlyGrowthCalculator.growthCalculate(BigDecimal.valueOf(100), BigDecimal.valueOf(200)))
                    .thenReturn(100.0);
            when(monthlyGrowthCalculator.growthCalculate(BigDecimal.valueOf(200), BigDecimal.valueOf(300)))
                    .thenReturn(50.0);

            List<MonthlySettlementResponse> result =
                    settlementMapper.toMonthlyResponses(list, 10);

            assertThat(result.get(0).growthRate()).isEqualTo(0.0);
            assertThat(result.get(1).growthRate()).isEqualTo(100.0);
            assertThat(result.get(2).growthRate()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("YearlySettlement 매핑 성공")
        void toYearlyResponses_success() {

            YearlySettlement y = HelperData.getYearlySettlement();

            List<YearlySettlementResponse> result =
                    settlementMapper.toYearlyResponses(List.of(y), 99);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).totalCount()).isEqualTo(99);
        }
    }

    // =============================================================
    // FAIL CASES
    // =============================================================
    @Nested
    @DisplayName("실패 케이스")
    class Fail {

        @Test
        @DisplayName("Daily 리스트 null 포함 시 NPE")
        void dailyList_containsNull() {

            List<DailySettlement> list = Arrays.asList(
                    HelperData.getDailySettlement(),
                    null
            );

            SettlementStatsDto stats = new SettlementStatsDto(10L, 10L);

            assertThatThrownBy(() ->
                    settlementMapper.toDailySettlementResponseList(list, stats)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Weekly 리스트 null 포함 시 NPE")
        void weeklyList_containsNull() {

            List<WeeklySettlement> list = Arrays.asList((WeeklySettlement) null);

            SettlementStatsDto stats = new SettlementStatsDto(10L, 10L);

            assertThatThrownBy(() ->
                    settlementMapper.toWeeklySettlementResponse(list, stats, (byte) 1)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Monthly 리스트 null 포함 시 NPE")
        void monthlyList_containsNull() {

            List<MonthlySettlement> list = Arrays.asList((MonthlySettlement) null);

            assertThatThrownBy(() ->
                    settlementMapper.toMonthlyResponses(list, 10)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Yearly 리스트 null 이면 NPE")
        void yearlyList_null() {

            assertThatThrownBy(() ->
                    settlementMapper.toYearlyResponses(null, 10)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Yearly 리스트에 null 요소 포함 시 NPE")
        void yearlyList_containsNull() {

            List<YearlySettlement> list = Arrays.asList(
                    HelperData.getYearlySettlement(),
                    null
            );

            assertThatThrownBy(() ->
                    settlementMapper.toYearlyResponses(list, 10)
            ).isInstanceOf(NullPointerException.class);
        }
    }
}