package com.homesweet.homesweetback.domain.settlement.util.calculator;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementCreateDto;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatsProjection;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

// 정산 금액 계산
@Component
@RequiredArgsConstructor
public class SettlementCalculator {
    private final GradeService gradeService;
    private final SettlementRepository settlementRepository;
    // 정산 금액 계산
    public Result getResult(Order order, User seller) {
        Long totalSales = order.getTotalAmount();
        if (order.getTotalAmount() < 0) {
            throw new BusinessException(ErrorCode.INVALID_TOTAL_AMOUNT);
        }

        BigDecimal fee = gradeService.calculateFeeforUser(BigDecimal.valueOf(order.getTotalAmount()), seller);
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.valueOf(order.getTotalAmount()).multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = BigDecimal.valueOf(order.getTotalAmount());
        BigDecimal settlementAmount = totalAmount.subtract(fee).subtract(refundAmount).setScale(2, RoundingMode.HALF_UP);
        return new Result(fee, refundAmount, vat, totalAmount, settlementAmount);
    }
    public record Result(BigDecimal fee, BigDecimal refundAmount, BigDecimal vat, BigDecimal totalAmount,
                         BigDecimal settlementAmount) {
    }
    // 환불된 정산 금액 계산
    public BigDecimal refundResult(Settlement settlement) {
        BigDecimal saleAmount = settlement.getSalesAmount();
        BigDecimal fee = settlement.getFee();
        BigDecimal vat = settlement.getVat();
        BigDecimal curSettlementAmount = Optional.ofNullable(settlement.getSettlementAmount()).orElse(BigDecimal.ZERO);
        BigDecimal refundAmount = saleAmount.add(vat).subtract(fee);
        BigDecimal refundSettlementAmount = curSettlementAmount.subtract(refundAmount);
        return refundSettlementAmount.max(BigDecimal.ZERO);
    }
    // 기간별 집계 계산
    public void accumulate(SettlementTotals acc, SettlementTotals add) {
        acc.add(add);
    }
    // 총 주문건수, 총 정산 완료 건수, 정산 완료율 (일별, 주별 사용)
//    @Cacheable(value = "weekly:stats", key = "#userId + ':' + #startDate + ':' + #endDate")
    public SettlementStats calculateStats(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        SettlementStatsProjection stats = settlementRepository.findStats(userId, start, end);

        long totalCount = stats.getTotalCount();  // 총 주문건수
        long completedCount = stats.getCompletedCount();
        double completedRate = totalCount == 0 ? 0.0 : Math.round(((double) completedCount * 100.0 / totalCount) * 10) / 10.0;  // 정산 완료율
        return new SettlementStats(totalCount, completedCount, completedRate);
    }
    public record SettlementStats(long totalCount, long completedCount, double completedRate) {
    }

    public Result getResult(SettlementCreateDto dto, User seller) {
        BigDecimal totalAmount = BigDecimal.valueOf(dto.totalAmount());
        BigDecimal refund = dto.refundAmount();
        BigDecimal fee = totalAmount.multiply(seller.getGrade().getFeeRate());
        BigDecimal vat = totalAmount.multiply(dto.vatRate());

        BigDecimal settlementAmount = totalAmount
                .subtract(fee)
                .subtract(vat)
                .subtract(refund);

        return new Result(
                totalAmount,
                fee,
                vat,
                refund,
                settlementAmount
        );
    }
}
