package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("연별 tasklet 단위 테스트")
class YearlySettlementTaskletTest {
    @InjectMocks
    private YearlySettlementTasklet yearlySettlementTasklet;

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementValidator settlementValidator;
    @Mock
    private SettlementSaver settlementSaver;
    @Mock
    private SettlementAggregator settlementAggregator;
    @Mock
    private GradeService gradeService;

    StepContribution contribution = mock(StepContribution.class);
    ChunkContext context = mock(ChunkContext.class);

    @BeforeEach
    void injectRealAggregator() {
        ReflectionTestUtils.setField(yearlySettlementTasklet, "cutoffString",
                "2025-11-25T00:00:00");
        SettlementCalculator calculator =
                new SettlementCalculator(gradeService, settlementRepository);

        SettlementAggregator realAggregator = new SettlementAggregator(calculator);

        // ★ 실제 aggregator 주입 (성공 케이스만 사용)
        ReflectionTestUtils.setField(
                yearlySettlementTasklet,
                "settlementAggregator",
                realAggregator
        );
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {
        @Test
        @DisplayName("정상적으로 연별 집계 성공한다.")
        void success_singleUser() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            RepeatStatus result = yearlySettlementTasklet.execute(contribution, context);

            assertThat(result).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver, times(1))
                    .saveYearly(eq(userId), eq((short) 2025), any(SettlementTotals.class));

        }

        @Test
        @DisplayName("여러 사용자가 있을 때 각각 집계가 수행된다.")
        void success_multiUsers() {
            List<Long> userIds = List.of(1L, 2L);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(userIds);

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            yearlySettlementTasklet.execute(contribution, context);

            verify(settlementSaver, times(2))
                    .saveYearly(anyLong(), eq((short) 2025), any());
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {
        @BeforeEach
        void useMockAggregator() {
            // 실패 테스트에서는 mock aggregator 사용해야함
            ReflectionTestUtils.setField(
                    yearlySettlementTasklet,
                    "settlementAggregator",
                    settlementAggregator
            );
        }

        @Test
        @DisplayName("cutoffString 파싱 오류 → 예외 발생")
        void execute_fail_invalidCutoff() {
            ReflectionTestUtils.setField(yearlySettlementTasklet,
                    "cutoffString", "INVALID");

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("validator.validateYearly()에서 BusinessException 발생")
        void execute_fail_validatorError() {
            Long userId = 11L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator).validateTotals(totals);

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("findDistinctUserIds에서 예외 → 전파됨")
        void fail_repositoryError() {

            given(settlementRepository.findDistinctUserIds())
                    .willThrow(new RuntimeException("DB ERROR"));

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("DB ERROR");
        }

        @Test
        @DisplayName("saveYearly 중 예외 발생")
        void fail_saveError() {

            Long userId = 11L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(any());

            doThrow(new RuntimeException("SAVE ERROR"))
                    .when(settlementSaver)
                    .saveYearly(anyLong(), anyShort(), any());

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("SAVE ERROR");
        }
    }
}