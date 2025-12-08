package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.dto.response.CachedResult;
import com.homesweet.homesweetback.domain.settlement.dto.response.CachedResultWithStats;
import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
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
public class WeeklySettlementService {
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final EmptyResponse emptyResponse;
    private final SettlementCacheService settlementCacheService;
    private final WeeklyDateRangeCalculator weeklyCalc;

    // 주별 데이터 조회(페이지 처리)
    @Transactional(readOnly = true)
    public Page<WeeklySettlementResponse> getWeeklySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 1) 캐시에서 content 가져오기
        List<WeeklySettlementResponse> content =
                settlementCacheService.getWeeklyContentCache(userId, startDate, endDate, pageable);


        if (content.isEmpty()) {
            WeeklyDateRangeCalculator.WeeklyDateRange range =
                    weeklyCalc.getWeeklyDateRange(startDate, endDate);
            return emptyResponse.createEmptyWeekly(range, pageable);
        }

        // 2) count 조회
        WeeklyDateRangeCalculator.WeeklyDateRange range =
                weeklyCalc.getWeeklyDateRange(startDate, endDate);

        long totalCount = weeklySettlementRepository.countByRange(
                userId,
                range.firstWeekStart(),
                range.lastWeekStartEx()
        );

        // 3) PageImpl 생성 후 반환
        return new PageImpl<>(content, pageable, totalCount);
    }

}