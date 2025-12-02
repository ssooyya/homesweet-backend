package com.homesweet.homesweetback.domain.settlement.service.unit;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.WeeklySettlementService;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("")
class WeeklySettlementServiceTest {
    @Mock
    private WeeklySettlementRepository weeklySettlementRepository;

    @Mock
    private SettlementCalculator settlementCalculator;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private EmptyResponse emptyResponse;

    @InjectMocks
    private WeeklySettlementService weeklySettlementService;

    @Mock
    private DailySettlementRepository dailySettlementRepository;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementAggregator settlementAggregator;

    @Mock
    private SettlementSaver settlementSaver;

    @Mock
    private GradeService gradeService;
    @Mock
    private SettlementRepository settlementRepository;

    Long userId = 1L;
    Pageable pageable = PageRequest.of(0, 10);

    @Nested
    @DisplayName("성공 케이스")
    class Success {
        private WeeklySettlementService weeklySettlementService;

        @BeforeEach
        void setUp() {
            // settlementCalculator는 mock이라서 실제 aggregator 안에서 호출되면 제대로 동작해야 함
            SettlementAggregator realAggregator = new SettlementAggregator(settlementCalculator);

            weeklySettlementService = new WeeklySettlementService(
                    weeklySettlementRepository,     // mock
                    dailySettlementRepository,      // mock
                    settlementCalculator,           // mock
                    emptyResponse,                  // mock
                    settlementMapper,               // mock
                    settlementValidator,            // mock
                    realAggregator,                 // ★ 실제 객체 주입!
                    settlementSaver                 // mock
            );
        }

        @Test
        @DisplayName("주별 집계가 정상적으로 조회된다")
        void getWeeklySummary_success() {
            // given
            LocalDate start = LocalDate.of(2025, 11, 10);
            LocalDate end = LocalDate.of(2025, 11, 16);

            WeeklyDateRangeCalculator.WeeklyDateRange range =
                    WeeklyDateRangeCalculator.getWeeklyDateRange(start, end);

            WeeklySettlement ws = new WeeklySettlement();
            Page<WeeklySettlement> page = new PageImpl<>(List.of(ws), pageable, 1);

            given(weeklySettlementRepository.findByWeeklySettlementByRange(
                    userId, range.firstWeekStart(), range.lastWeekStartEx(), pageable
            )).willReturn(page);

            SettlementCalculator.SettlementStats stats =
                    new SettlementCalculator.SettlementStats(10, 5, 50.0);

            given(settlementCalculator.calculateStats(userId, start, end))
                    .willReturn(stats);

            WeeklySettlementResponse mapped = new WeeklySettlementResponse(
                    (short) 2025, (byte) 11, range.week(),
                    start, end,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    stats.completedRate(), stats.totalCount()
            );
            given(settlementMapper.toWeeklySettlementResponse(
                    page.getContent(), stats, range.week()
            )).willReturn(List.of(mapped));

            // when
            Page<WeeklySettlementResponse> result =
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);

            WeeklySettlementResponse res = result.getContent().get(0);

            assertThat(res.year()).isEqualTo((short) 2025);
            assertThat(res.month()).isEqualTo((byte) 11);
            assertThat(res.completedRate()).isEqualTo(50.0);
            assertThat(result.getTotalElements()).isEqualTo(stats.totalCount());

            verify(weeklySettlementRepository, times(1))
                    .findByWeeklySettlementByRange(userId, range.firstWeekStart(), range.lastWeekStartEx(), pageable);

            verify(settlementCalculator, times(1))
                    .calculateStats(userId, start, end);
        }

        @Test
        @DisplayName("주별 데이터가 없으면 빈 Page 응답을 반환한다")
        void getWeeklySummary_empty_success() {
            // given
            LocalDate start = LocalDate.of(2025, 11, 10);
            LocalDate end = LocalDate.of(2025, 11, 16);

            WeeklyDateRangeCalculator.WeeklyDateRange range =
                    WeeklyDateRangeCalculator.getWeeklyDateRange(start, end);

            Page<WeeklySettlement> emptyPage =
                    new PageImpl<>(List.of(), pageable, 0);

            given(weeklySettlementRepository.findByWeeklySettlementByRange(
                    userId, range.firstWeekStart(), range.lastWeekStartEx(), pageable
            )).willReturn(emptyPage);

            Page<WeeklySettlementResponse> emptyResponsePage =
                    new PageImpl<>(List.of(), pageable, 0);

            given(emptyResponse.createEmptyWeekly(range, pageable))
                    .willReturn(emptyResponsePage);

            // when
            Page<WeeklySettlementResponse> result =
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();

            verify(emptyResponse, times(1))
                    .createEmptyWeekly(range, pageable);
        }
        @Test
        @DisplayName("주별 집계가 올바르게 계산되고 저장된다.")
        void getWeeklySettlement_success() {
            // given
            Long userId = 1L;
            LocalDate weekStart = LocalDate.of(2025, 11, 10);
            LocalDate weekEnd = LocalDate.of(2025, 11, 16);

            DailySettlement d1 = HelperData.getDailySettlementWithDate(LocalDate.of(2025, 11, 10));
            DailySettlement d2 = HelperData.getDailySettlementWithDate(LocalDate.of(2025, 11, 11));

            List<DailySettlement> settlements = List.of(d1, d2);

            Map<LocalDate, SettlementTotals> aggregated = Map.of(
                    LocalDate.of(2025, 11, 10), SettlementTotals.empty()
            );
            // repository mock
            given(dailySettlementRepository.findByDailySettlement(userId))
                    .willReturn(settlements);

            // validator mock — 예외 없이 통과
            doNothing().when(settlementValidator).validateWeekly(settlements);
            // when
            weeklySettlementService.getWeeklySettlement(userId, weekStart, weekEnd);


            verify(settlementSaver, times(1))
                    .saveWeekly(anyLong(),
                            anyShort(),
                            anyByte(),
                            any(LocalDate.class),
                            any(LocalDate.class),
                            any(SettlementTotals.class));
        }
    }
    @Nested
    @DisplayName("실패 케이스")
    class fail {
        @Test
        @DisplayName("Repository 예외 발생 시 그대로 전달된다")
        void getWeeklySummary_fail_repositoryThrows() {
            // given
            LocalDate start = LocalDate.of(2025, 11, 10);
            LocalDate end = LocalDate.of(2025, 11, 16);
            WeeklyDateRangeCalculator.WeeklyDateRange range =
                    WeeklyDateRangeCalculator.getWeeklyDateRange(start, end);

            given(weeklySettlementRepository.findByWeeklySettlementByRange(
                    userId, range.firstWeekStart(), range.lastWeekStartEx(), pageable
            )).willThrow(new RuntimeException("DB ERROR"));

            // when & then
            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySummary(userId, start, end, pageable)
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB ERROR");
        }

        @Test
        @DisplayName("일별 데이터 없으면 예외 발생")
        void getWeeklySettlement_fail_noDailyData() {
            Long userId = 1L;

            given(dailySettlementRepository.findByDailySettlement(userId))
                    .willReturn(List.of());

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator).validateWeekly(List.of());

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(userId, LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(BusinessException.class);

            verify(settlementSaver, never()).saveWeekly(any(),any(),any(),any(), any(), any());
        }

        @Test
        @DisplayName("집계 중 aggregator가 null을 반환하면 예외 발생")
        void getWeeklySettlement_fail_aggregatorNull() {
            Long userId = 1L;

            DailySettlement d1 = HelperData.getDailySettlementWithDate(LocalDate.now());
            List<DailySettlement> settlements = List.of(d1);

            given(dailySettlementRepository.findByDailySettlement(userId))
                    .willReturn(settlements);

            doNothing().when(settlementValidator).validateWeekly(settlements);

            given(settlementAggregator.aggregate(anyList(), any(), any()))
                    .willReturn(null);

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(userId, LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(NullPointerException.class);

            verify(settlementSaver, never()).saveWeekly(any(), any(),any(),any(),any(), any());
        }
        @Test
        @DisplayName("저장(saveWeekly) 중 예외 발생하면 롤백")
        void getWeeklySettlement_fail_saveError() {
            Long userId = 1L;

            DailySettlement d1 = HelperData.getDailySettlementWithDate(LocalDate.of(2025, 11, 10));
            List<DailySettlement> settlements = List.of(d1);

            Map<LocalDate, SettlementTotals> aggregated =
                    Map.of(LocalDate.of(2025, 11, 10), SettlementTotals.empty());

            given(dailySettlementRepository.findByDailySettlement(userId))
                    .willReturn(settlements);

            doNothing().when(settlementValidator).validateWeekly(settlements);

            given(settlementAggregator.aggregate(anyList(), any(), any()))
                    .willReturn((Map) aggregated);

            // saveWeekly에서 예외 발생시키기
            doThrow(new RuntimeException("DB error"))
                    .when(settlementSaver).saveWeekly(any(), any(),any(),any(),any(), any());

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(userId, LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
        @Test
        @DisplayName("DailySettlement 중 null 요소가 존재하면 NullPointerException")
        void getWeeklySettlement_fail_nullDailySettlementElement() {
            Long userId = 1L;

            List<DailySettlement> settlements = Arrays.asList((DailySettlement) null);

            given(dailySettlementRepository.findByDailySettlement(userId))
                    .willReturn(settlements);

            doNothing().when(settlementValidator).validateWeekly(settlements);
            given(settlementAggregator.aggregate(anyList(), any(), any()))
                    .willThrow(new NullPointerException("null daily settlement"));

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(userId, LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}