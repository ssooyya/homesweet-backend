package com.homesweet.homesweetback.domain.settlement.service.unit;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.dto.response.MonthlySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementStatsDto;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.MonthlySettlementService;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import org.junit.jupiter.api.*;
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
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("월별 서비스 단위 테스트")
@Disabled
class MonthlySettlementServiceTest {

    @InjectMocks
    private MonthlySettlementService monthlySettlementService;

    @Mock
    private MonthlySettlementRepository monthlySettlementRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private WeeklySettlementRepository weeklySettlementRepository;

    @Mock
    private MonthlyDateRangeCalculator monthlyDateRangeCalculator;

    @Mock
    private EmptyResponse emptyResponse;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementSaver settlementSaver;

    @Mock
    private SettlementCalculator settlementCalculator;

    @Nested
    @DisplayName("getMonthlySummary 성공 케이스")
    class SummarySuccess {

        @Test
        @DisplayName("월별 요약 조회 성공")
        void getMonthlySummary_success() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 1, 10);
            LocalDate end = LocalDate.of(2025, 3, 20);
            Pageable pageable = PageRequest.of(0, 10);

            // 1. Range
            MonthlyDateRangeCalculator.MonthlyDateRange range =
                    new MonthlyDateRangeCalculator.MonthlyDateRange(
                            YearMonth.of(2025, 1),
                            YearMonth.of(2025, 3),
                            LocalDateTime.of(2025, 1, 1, 0, 0),
                            LocalDateTime.of(2025, 4, 1, 0, 0),
                            (short) 2025, (byte) 1,
                            (short) 2025, (byte) 3
                    );

            given(monthlyDateRangeCalculator.MonthlyDateRangeCalculate(start, end))
                    .willReturn(range);

            // 2. totalCount
            given(settlementRepository.findStats(userId, range.from(), range.toExclusive()))
                    .willReturn(new SettlementStatsDto(10L, 0L));

            // 3. repository page
            MonthlySettlement m1 = MonthlySettlement.builder()
                    .year((short) 2025)
                    .month((byte) 1)
                    .totalSales(BigDecimal.valueOf(100000))
                    .totalFee(BigDecimal.valueOf(5000))
                    .totalVat(BigDecimal.valueOf(10000))
                    .totalRefund(BigDecimal.ZERO)
                    .totalSettlement(BigDecimal.valueOf(85000))
                    .build();

            Page<MonthlySettlement> page = new PageImpl<>(List.of(m1), pageable, 1);

            given(monthlySettlementRepository.findByMonthlySettlementByRange(
                    eq(userId),
                    eq((short) 2025), eq((byte) 1),
                    eq((short) 2025), eq((byte) 3),
                    eq(pageable)
            )).willReturn(page);

            // 4. mapper
            MonthlySettlementResponse mapped =
                    new MonthlySettlementResponse(
                            (short) 2025, (byte) 1,
                            BigDecimal.valueOf(100000),
                            BigDecimal.valueOf(5000),
                            BigDecimal.valueOf(10000),
                            BigDecimal.ZERO,
                            BigDecimal.valueOf(85000),
                            0.0, // completedRate
                            10L  // totalCount
                    );

            given(settlementMapper.toMonthlyResponses(page.getContent(), 10L))
                    .willReturn(List.of(mapped));

            // when
            Page<MonthlySettlementResponse> result =
                    monthlySettlementService.getMonthlySummary(userId, start, end, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).year()).isEqualTo((short) 2025);
        }

        @Test
        @DisplayName("월별 데이터가 없으면 EmptyResponse 반환")
        void getMonthlySummary_empty() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            Pageable pageable = PageRequest.of(0, 10);

            MonthlyDateRangeCalculator.MonthlyDateRange range =
                    new MonthlyDateRangeCalculator.MonthlyDateRange(
                            YearMonth.of(2025, 1),
                            YearMonth.of(2025, 1),
                            LocalDateTime.of(2025, 1, 1, 0, 0),
                            LocalDateTime.of(2025, 2, 1, 0, 0),
                            (short) 2025, (byte) 1,
                            (short) 2025, (byte) 1
                    );

            given(monthlyDateRangeCalculator.MonthlyDateRangeCalculate(start, end))
                    .willReturn(range);

            given(monthlySettlementRepository.findByMonthlySettlementByRange(
                    anyLong(), anyShort(), anyByte(), anyShort(), anyByte(), eq(pageable)
            )).willReturn(Page.empty(pageable));

            // empty response mock
            given(emptyResponse.createEmptyMonthly(range.fromYM(), pageable))
                    .willReturn(new PageImpl<>(
                            List.of(new MonthlySettlementResponse(
                                    (short) 2025,
                                    (byte) 1,
                                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                    BigDecimal.ZERO, BigDecimal.ZERO,
                                    0.0,
                                    0L)),
                            pageable, 1));

            // when
            Page<MonthlySettlementResponse> result =
                    monthlySettlementService.getMonthlySummary(userId, start, end, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).totalSales()).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getMonthlySummary 실패 케이스")
    class SummaryFailure {

        @Test
        @DisplayName("repository 예외 전파")
        void repoError() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 31);
            Pageable pageable = PageRequest.of(0, 10);

            MonthlyDateRangeCalculator.MonthlyDateRange range =
                    new MonthlyDateRangeCalculator.MonthlyDateRange(
                            YearMonth.of(2025, 1),
                            YearMonth.of(2025, 1),
                            LocalDateTime.now(), LocalDateTime.now(),
                            (short) 2025, (byte) 1,
                            (short) 2025, (byte) 1
                    );

            given(monthlyDateRangeCalculator.MonthlyDateRangeCalculate(start, end))
                    .willReturn(range);

            given(monthlySettlementRepository.findByMonthlySettlementByRange(
                    anyLong(), anyShort(), anyByte(), anyShort(), anyByte(), eq(pageable)))
                    .willThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() ->
                    monthlySettlementService.getMonthlySummary(userId, start, end, pageable)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }
}
