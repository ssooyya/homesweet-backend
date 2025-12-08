package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.dto.response.YearlySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.YearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.YearlyDateRangeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YearlySettlementService {
    private final YearlySettlementRepository yearlySettlementRepository;
    private final YearlyDateRangeCalculator yearlyCalc;
    private final EmptyResponse emptyResponse;
    private final SettlementCacheService settlementCacheService;

    // 연별 데이터 조회
    @Transactional(readOnly = true)
    public Page<YearlySettlementResponse> getYearlySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 1) 캐시에서 content 가져오기
        List<YearlySettlementResponse> content =
                settlementCacheService.getYearlyContentCache(userId, startDate, endDate, pageable);

        if (content.isEmpty()) {
            int year = startDate.getYear();
            return emptyResponse.createEmptyYearly((short) year, pageable);
        }

        // 2) count 조회
        YearlyDateRangeCalculator.YearlyDateRange range =
                yearlyCalc.calculate(startDate, endDate);

        long totalCount = yearlySettlementRepository.countByRange(
                userId,
                range.fromYear(),
                range.toYearExclusive()
        );

        // 3) Page 반환
        return new PageImpl<>(content, pageable, totalCount);
    }
}