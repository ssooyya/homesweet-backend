package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.data.BatchHelperData;
import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatusUpdater;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("일별 집계 tasklet 단위테스트")
class DailySettlementTaskletTest {
    @InjectMocks
    private DailySettlementTasklet dailySettlementTasklet;

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementValidator settlementValidator;
    @Mock
    private SettlementAggregator settlementAggregator;
    @Mock
    private SettlementSaver settlementSaver;
    @Mock
    private SettlementStatusUpdater settlementStatusUpdater;

    @Mock
    private GradeService gradeService;

    StepContribution contribution = mock(StepContribution.class);
    ChunkContext context = mock(ChunkContext.class);

    // aggregate 실제 주입
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                dailySettlementTasklet,
                "cutoffString",
                "2025-11-25T00:00:00"
        );
        SettlementCalculator calculator = new SettlementCalculator(
                gradeService, settlementRepository
        );

        SettlementAggregator aggregator = new SettlementAggregator(calculator);

        ReflectionTestUtils.setField(
                dailySettlementTasklet,   // 또는 monthlyTasklet
                "settlementAggregator",
                aggregator
        );
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {
        @Test
        @DisplayName("정상적으로 일별 집계가 실행된다")
        void execute_success_singleUser() {
            Long userId = 1L;
            SettlementTotals totals = new SettlementTotals(
                    BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.valueOf(8)
            );

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            // validator OK
            doNothing().when(settlementValidator).validateTotals(totals);

            doNothing().when(settlementSaver).saveDaily(anyLong(), any(), any());
            doNothing().when(settlementStatusUpdater).markDailyCompleted(anyLong(), any(), any());

            RepeatStatus status =
                    dailySettlementTasklet.execute(contribution, context);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver).saveDaily(eq(userId), any(), eq(totals));
            verify(settlementStatusUpdater).markDailyCompleted(eq(userId), any(), any());
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {
        @BeforeEach
        void removeRealAggregator() {
            // 원래 mock으로 돌아가게 설정
            ReflectionTestUtils.setField(
                    dailySettlementTasklet,
                    "settlementAggregator",
                    settlementAggregator // mock 으로 되돌림
            );
        }

        @Test
        @DisplayName("cutoffString 파싱 실패 시 DateTimeParseException 발생")
        void execute_fail_invalidCutoff() {

            ReflectionTestUtils.setField(dailySettlementTasklet, "cutoffString", "INVALID_DATE");

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("settlementValidator.validateDaily() 에서 BusinessException 발생")
        void execute_fail_validatorThrows() {

            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator).validateTotals(any());

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.SETTLEMENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("saveDaily() 내부 예외 발생 시 실패")
        void execute_fail_saveDailyThrows() {

            Long userId = 1L;
            SettlementTotals totals = BatchHelperData.totals();

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doThrow(new RuntimeException("save fail"))
                    .when(settlementSaver).saveDaily(anyLong(), any(), any());

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("save fail");
        }

        @Test
        @DisplayName("repository 에러 발생 시 예외 전파된다")
        void execute_fail_repoError() {

            given(settlementRepository.findDistinctUserIds())
                    .willThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }
}