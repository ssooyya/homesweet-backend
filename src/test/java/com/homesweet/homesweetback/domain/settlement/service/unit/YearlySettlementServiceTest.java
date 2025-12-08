package com.homesweet.homesweetback.domain.settlement.service.unit;

import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.dto.response.YearlySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.YearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.SettlementCacheService;
import com.homesweet.homesweetback.domain.settlement.service.YearlySettlementService;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.YearlyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("연별 서비스 테스트")
class YearlySettlementServiceTest {
    @Mock
    private MonthlySettlementRepository monthlySettlementRepository;
    @Mock
    private SettlementValidator settlementValidator;
    @Mock
    private SettlementSaver settlementSaver;
    @InjectMocks
    private YearlySettlementService yearlySettlementService;
    @Mock
    private YearlySettlementRepository yearlySettlementRepository;
    @Mock
    private EmptyResponse emptyResponse;

    @Mock
    private SettlementCacheService settlementCacheService;
    @Mock
    private YearlyDateRangeCalculator yearlyCalc;


    Long userId = 1L;
    Pageable pageable = PageRequest.of(0, 10);
    LocalDate start = LocalDate.of(2025, 1, 1);
    LocalDate end = LocalDate.of(2025, 12, 31);

    YearlyDateRangeCalculator.YearlyDateRange range =
            new YearlyDateRangeCalculator.YearlyDateRange(
                    YearMonth.of(2025, 1),
                    (short) 2025,
                    (short) 2026,
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 1, 1, 0, 0)
            );



    // ----------------------------------------------------
    // SUCCESS
    // ----------------------------------------------------
    @Test
    @DisplayName("캐시 HIT → 바로 반환")
    void cacheHit_success() {

        YearlySettlementResponse res = new YearlySettlementResponse(
                (short) 2025,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                10L
        );

        given(settlementCacheService.getYearlyContentCache(userId, start, end, pageable))
                .willReturn(List.of(res));

        given(yearlyCalc.calculate(start, end)).willReturn(range);

        given(yearlySettlementRepository.countByRange(userId, (short) 2025, (short) 2026))
                .willReturn(10L);

        Page<YearlySettlementResponse> result =
                yearlySettlementService.getYearlySummary(userId, start, end, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(10L);
        verify(yearlyCalc, times(1)).calculate(start, end);
        verify(yearlySettlementRepository, times(1))
                .countByRange(userId, (short) 2025, (short) 2026);
    }


    @Test
    @DisplayName("캐시 MISS → EmptyResponse 호출")
    void cacheMiss_empty() {

        given(settlementCacheService.getYearlyContentCache(userId, start, end, pageable))
                .willReturn(List.of());

        Page<YearlySettlementResponse> empty =
                new PageImpl<>(List.of(), pageable, 0);

        given(emptyResponse.createEmptyYearly((short) 2025, pageable))
                .willReturn(empty);

        Page<YearlySettlementResponse> result =
                yearlySettlementService.getYearlySummary(userId, start, end, pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }


    // ----------------------------------------------------
    // FAILURE
    // ----------------------------------------------------
    @Test
    @DisplayName("캐시 조회 오류 → 예외 전파")
    void cacheFail() {

        given(settlementCacheService.getYearlyContentCache(userId, start, end, pageable))
                .willThrow(new RuntimeException("CACHE ERROR"));

        assertThatThrownBy(() ->
                yearlySettlementService.getYearlySummary(userId, start, end, pageable)
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("CACHE ERROR");
    }


    @Test
    @DisplayName("count 조회 중 오류 발생 → 예외 전파")
    void countFail() {

        YearlySettlementResponse res = new YearlySettlementResponse(
                (short) 2025,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                10L
        );

        given(settlementCacheService.getYearlyContentCache(userId, start, end, pageable))
                .willReturn(List.of(res));

        given(yearlyCalc.calculate(start, end)).willReturn(range);

        given(yearlySettlementRepository.countByRange(any(), any(), any()))
                .willThrow(new RuntimeException("DB ERROR"));

        assertThatThrownBy(() ->
                yearlySettlementService.getYearlySummary(userId, start, end, pageable)
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("DB ERROR");
    }
}