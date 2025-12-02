package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.settlement.entity.QSettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl.CustomSettlementRepositoryImpl;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomSettlementRepositoryImplTest {
    @Mock
    JPAQueryFactory jpaQueryFactory;

    @Mock(answer = Answers.RETURNS_SELF)
    JPAUpdateClause jpaUpdateClause;

    @InjectMocks
    CustomSettlementRepositoryImpl customSettlementRepository;

    @Test
    void applyRefundAmount_success() {

        QSettlement q = QSettlement.settlement;

        // update() → 첫 시작점만 stubbing
        when(jpaQueryFactory.update(q)).thenReturn(jpaUpdateClause);

        // execute()만 stubbing (나머지는 RETURNS_SELF 때문에 체인됨)
        when(jpaUpdateClause.execute()).thenReturn(1L);

        // when
        int result = customSettlementRepository.applyRefundAmount(
                1L,
                BigDecimal.valueOf(10000)
        );
        // then
        assertThat(result).isEqualTo(1);

        // verify (주의: set()은 절대 verify 하지 않는다)
        verify(jpaQueryFactory).update(q);
        verify(jpaUpdateClause).where(any());
        verify(jpaUpdateClause).execute();
    }
    @Test
    void applyRefundAmount_fail_exceptionThrown() {
        QSettlement q = QSettlement.settlement;
        when(jpaQueryFactory.update(q)).thenReturn(jpaUpdateClause);
        when(jpaUpdateClause.execute()).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() ->
                customSettlementRepository.applyRefundAmount(
                        1L,
                        BigDecimal.valueOf(10000))
        ).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");

        verify(jpaQueryFactory).update(q);
        verify(jpaUpdateClause).where(any());
        verify(jpaUpdateClause).execute();
    }

}
