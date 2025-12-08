package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.data.BatchHelperData;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("주별 tasklet 단위 테스트")
class WeeklySettlementTaskletTest {

    @InjectMocks
    private WeeklySettlementTasklet weeklySettlementTasklet;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementSaver settlementSaver;

    @Mock
    private GradeService gradeService;

    StepContribution contribution = mock(StepContribution.class);
    ChunkContext context = mock(ChunkContext.class);

    private final LocalDate cutoff = LocalDate.of(2025, 11, 25);
    private final LocalDate weekStart = LocalDate.of(2025, 11, 24);
    private final LocalDate weekEnd = weekStart.plusDays(6);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(weeklySettlementTasklet, "cutoffString", "2025-11-25T00:00:00");
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("정상적으로 주별 집계가 수행된다")
        void execute_success_singleUser() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            SettlementTotals totals = SettlementTotals.empty();
            given(settlementRepository.sumTotals(eq(userId), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            RepeatStatus status = weeklySettlementTasklet.execute(contribution, context);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver).saveWeekly(
                    eq(userId),
                    eq((short) weekStart.getYear()),
                    eq((byte) weekStart.getMonthValue()),
                    eq(weekStart),
                    eq(weekEnd),
                    eq(totals)
            );
        }

        @Test
        @DisplayName("여러 사용자에 대해 주별 집계가 수행된다")
        void execute_success_multiUser() {
            List<Long> userIds = List.of(1L, 2L);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(userIds);

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            weeklySettlementTasklet.execute(contribution, context);

            verify(settlementSaver, times(2))
                    .saveWeekly(anyLong(), anyShort(), anyByte(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("cutoffString 파싱 실패 → 예외 발생")
        void execute_fail_invalidCutoff() {

            ReflectionTestUtils.setField(
                    weeklySettlementTasklet,
                    "cutoffString",
                    "INVALID_DATE"
            );

            assertThatThrownBy(() ->
                    weeklySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("sumTotals가 null이면 validateTotals에서 BusinessException 발생")
        void execute_fail_nullTotals() {
            Long userId = 1L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(null);

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator).validateTotals(null);

            assertThatThrownBy(() ->
                    weeklySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("sumTotals 실행 도중 예외 발생")
        void execute_fail_sumTotalsThrows() {

            Long userId = 1L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            doThrow(new RuntimeException("sum error"))
                    .when(settlementRepository)
                    .sumTotals(anyLong(), any(), any());

            assertThatThrownBy(() ->
                    weeklySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("sum error");
        }

        @Test
        @DisplayName("saveWeekly 중 예외 발생")
        void execute_fail_saveWeeklyThrows() {
            Long userId = 1L;
            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR))
                    .when(settlementSaver)
                    .saveWeekly(any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() ->
                    weeklySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class);
        }
    }
}
