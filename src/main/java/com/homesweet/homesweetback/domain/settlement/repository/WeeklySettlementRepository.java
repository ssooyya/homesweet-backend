package com.homesweet.homesweetback.domain.settlement.repository;

import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface WeeklySettlementRepository extends JpaRepository<WeeklySettlement, Long> {
    // 주별 조회
    @Query("SELECT w FROM WeeklySettlement w WHERE w.userId = :userId")
    List<WeeklySettlement> findByWeeklySettlement(@Param("userId") Long userId);


    // 주별 집계 조회
    @Query(value = """
    SELECT w FROM WeeklySettlement w
    WHERE w.userId = :userId
     AND w.weekStartDate >= :startDate
     AND w.weekStartDate < :endDate
    ORDER BY w.weekStartDate ASC
    """, countQuery = """
    SELECT COUNT(w) FROM WeeklySettlement w
    WHERE w.userId = :userId
     AND w.weekStartDate >= :startDate
     AND w.weekStartDate < :endDate
    """)
    Page<WeeklySettlement> findByWeeklySettlementByRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT INTO weekly_settlements (
            user_id, year_value, month_value, week_start_date,  week_end_date, total_sales, total_fee, total_vat, total_refund, total_settlement
        )
        VALUES (
            :userId, :yearValue, :monthValue, :weekStartDate, :weekEndDate, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
        ) AS new
        ON DUPLICATE KEY UPDATE
            total_sales = new.total_sales,
            total_fee = new.total_fee,
            total_vat = new.total_vat,
            total_refund = new.total_refund,
            total_settlement = new.total_settlement,
            month_value = new.month_value,
            week_end_date = new.week_end_date
        """, nativeQuery = true)
    void upsertWeekly(
            @Param("userId") Long userId,
            @Param("yearValue") Short yearValue,
            @Param("monthValue") Byte monthValue,
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("weekEndDate") LocalDate weekEndDate,
            @Param("totalSales") BigDecimal totalSales,
            @Param("totalFee") BigDecimal totalFee,
            @Param("totalVat") BigDecimal totalVat,
            @Param("totalRefund") BigDecimal totalRefund,
            @Param("totalSettlement") BigDecimal totalSettlement
    );
}
