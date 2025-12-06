package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.dto.response.YearlySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.YearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.YearlyDateRangeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class YearlySettlementService {
    private final YearlySettlementRepository yearlySettlementRepository;
    private final YearlyDateRangeCalculator yearlyDateRangeCalculator;
    private final EmptyResponse emptyResponse;
    private final SettlementCacheService settlementCacheService;

    // redis cache 적용

    // 연별 데이터 조회
    @Transactional(readOnly = true)
    public Page<YearlySettlementResponse> getYearlySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        short fromYear = (short) startDate.getYear();
        short toYear = (short) endDate.getYear();
        //1. 연도 계산
        YearlyDateRangeCalculator.YearlyDateRange range =
                yearlyDateRangeCalculator.calculate(startDate, endDate);

        // 2. 페이지로 정산일시 기준 연별 정산목록 조회
//        Page<YearlySettlement> yearlySettlements = getYearlySettlements(userId, range.fromYear(), range.toYearExclusive(), pageable);
        Page<YearlySettlement> pageInfo =
                yearlySettlementRepository.findByYearlySettlementByRange(
                        userId, fromYear, toYear, pageable
                );
        long totalCount = pageInfo.getTotalElements();
        List<YearlySettlementResponse> yearlySettlements =
        settlementCacheService.getYearlyContentCache(userId, startDate, endDate, pageable);
        // 3. 기간 전체의 총 주문 건수
//        long totalCount = settlementRepository.countAllByOrderedAt(userId, range.fromDateTime(),
//                range.toDateTimeExclusive());

        // 4. 데이터가 존재하지 않으면 0 반환
        if (yearlySettlements.isEmpty()) {
            return emptyResponse.createEmptyYearly(range.fromYearMonth(), pageable);
        }

        // 5. 응답 반환
//        List<YearlySettlementResponse> yearlySettlement =
//                settlementMapper.toYearlyResponses(yearlySettlements, totalCount);

        // 6. 페이지 반환
        return new PageImpl<>(yearlySettlements, pageable, totalCount);
    }
    private Page<YearlySettlement> getYearlySettlements(Long userId, short fromYear,short toYearExclusive, Pageable pageable) {
        return yearlySettlementRepository.findByYearlySettlementByRange(userId, fromYear, toYearExclusive, pageable);
    }
    // 연별 집계

}