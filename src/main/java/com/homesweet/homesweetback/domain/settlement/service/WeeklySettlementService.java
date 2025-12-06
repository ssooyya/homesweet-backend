package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
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
public class WeeklySettlementService {
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final SettlementCalculator settlementCalculator;
    private final EmptyResponse emptyResponse;
    private final SettlementCacheService settlementCacheService;

    // 주별 데이터 조회(페이지 처리)
    @Transactional(readOnly = true)
    public Page<WeeklySettlementResponse> getWeeklySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 1. 주 시작일: 월요일, 주 종료일: 일요일 , 주차 구하기
        WeeklyDateRangeCalculator.WeeklyDateRange range =
                WeeklyDateRangeCalculator.getWeeklyDateRange(startDate, endDate);

        // 2. 페이지로 정산일시 기준 월별 정산목록 조회
        List<WeeklySettlementResponse> weeklySettlementsPage = settlementCacheService.getWeeklyContentCache(userId, startDate, endDate, pageable);
        Page<WeeklySettlement> pageinfo = findWeeklySettlements(userId, pageable, range.firstWeekStart(), range.lastWeekStartEx());
        long totalCount = pageinfo.getTotalElements();
        // 3. 기간 전체의 총 주문 건수/총 정산 완료 건수/정산 완료율
        SettlementCalculator.SettlementStats stats = settlementCalculator.calculateStats(userId, startDate, endDate);

        // 4. 데이터가 존재하지 않으면 0 반환
        if (weeklySettlementsPage.isEmpty()) {
            return emptyResponse.createEmptyWeekly(range, pageable);
        }
        return new PageImpl<>(weeklySettlementsPage, pageable, totalCount);
    }

    private Page<WeeklySettlement> findWeeklySettlements(Long userId, Pageable pageable, LocalDate firstWeekStart, LocalDate lastWeekStartEx) {
        Page<WeeklySettlement> weeklySettlements = weeklySettlementRepository.findByWeeklySettlementByRange(userId, firstWeekStart, lastWeekStartEx, pageable);
        return weeklySettlements;
    }
}