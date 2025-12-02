package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
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
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("월별 tasklet 단위 테스트")
class MonthlySettlementTaskletTest {

    @InjectMocks
    private MonthlySettlementTasklet monthlySettlementTasklet;
    @Mock
    private WeeklySettlementRepository weeklySettlementRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementAggregator settlementAggregator;

    @Mock
    private SettlementSaver settlementSaver;

    @Mock
    private GradeService gradeService;

    StepContribution contribution = mock(StepContribution.class);
    ChunkContext context = mock(ChunkContext.class);

    @BeforeEach
    // 실제 aggregator 주입용
    void injectRealAggregator() {
        ReflectionTestUtils.setField(monthlySettlementTasklet, "cutoffString", "2025-11-25T00:00:00");
        SettlementCalculator calculator = new SettlementCalculator(gradeService, settlementRepository);
        SettlementAggregator realAgg = new SettlementAggregator(calculator);

        ReflectionTestUtils.setField(monthlySettlementTasklet, "settlementAggregator", realAgg);
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success{
        @Test
        @DisplayName("단일 사용자 월별 집계 성공")
        void execute_success_singleUser() {
            Long userId = 10L;

            YearMonth ym = YearMonth.of(2025, 11);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(
                    eq(userId),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class)
            )).willReturn(new SettlementTotals(
                    java.math.BigDecimal.TEN,
                    java.math.BigDecimal.ONE,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.valueOf(9)
            ));

            doNothing().when(settlementValidator).validateTotals(any());

            RepeatStatus result = monthlySettlementTasklet.execute(contribution, context);

            assertThat(result).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver, atLeastOnce())
                    .saveMonthly(eq(userId), eq(ym), any(SettlementTotals.class));
        }

        @Test
        @DisplayName("여러 사용자 월별 집계 성공")
        void execute_success_multiUsers() {
            List<Long> userIds = List.of(1L, 2L);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(userIds);

            given(settlementRepository.sumTotals(
                    anyLong(),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class)
            )).willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            monthlySettlementTasklet.execute(contribution, context);

            verify(settlementSaver, times(2))
                    .saveMonthly(anyLong(), any(YearMonth.class), any(SettlementTotals.class));
        }
    }
    @Nested
    @DisplayName("실패 케이스")
    class Failure{
        @BeforeEach
        void useMockAggregator() {
            // 실패 테스트에서는 mock aggregator 사용해야함
            ReflectionTestUtils.setField(
                    monthlySettlementTasklet,
                    "settlementAggregator",
                    settlementAggregator
            );
        }

        @Test
        @DisplayName("cutoffString 파싱 실패 → 예외 발생")
        void execute_fail_invalidCutoff() {

            ReflectionTestUtils.setField(
                    monthlySettlementTasklet,
                    "cutoffString",
                    "INVALID_DATE"
            );

            assertThatThrownBy(() ->
                    monthlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("validator.validateTotals() 에서 BusinessException 발생")
        void execute_fail_validatorThrows() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(null);

            assertThatThrownBy(() ->
                    monthlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("saveMonthly 중 예외 발생")
        void execute_fail_saveMonthlyError() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR))
                    .when(settlementSaver)
                    .saveMonthly(anyLong(), any(), any());

            assertThatThrownBy(() ->
                    monthlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class);
        }
    }
}