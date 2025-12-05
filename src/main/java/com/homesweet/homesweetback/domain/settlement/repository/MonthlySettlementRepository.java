package com.homesweet.homesweetback.domain.settlement.repository;

import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {
    // 월별 정산 집계 조회
    @Query("SELECT m FROM MonthlySettlement m WHERE m.userId = :userId")
    List<MonthlySettlement> findByMonthlySettlement(@Param("userId") Long userId);
    // 월별 집계 조회
    @Query(value = """
    SELECT m FROM MonthlySettlement m
    WHERE m.userId = :userId
     AND (m.year >= :fromYear OR (m.year = :fromYear AND m.month >= :fromMonth))
     AND (m.year < :toYear OR (m.year = :toYear AND m.month >= :toMonth))
    ORDER BY m.year DESC, m.month DESC
    """, countQuery = """
    SELECT COUNT(m) FROM MonthlySettlement m
    WHERE m.userId = :userId
     AND (m.year >= :fromYear OR (m.year = :fromYear AND m.month >= :fromMonth))
     AND (m.year < :toYear OR (m.year = :toYear AND m.month >= :toMonth))
    """)
    Page<MonthlySettlement> findByMonthlySettlementByRange(@Param("userId") Long userId, @Param("fromYear") Short fromYear, @Param("fromMonth") Byte fromMonth, @Param("toYear") Short toYear, @Param("toMonth") Byte toMonth, Pageable pageable);

        @Modifying
        @Query(value = """
            INSERT INTO monthly_settlements (
                user_id, year_value, month_value, total_sales, total_fee, total_vat, total_refund, total_settlement
            )
            VALUES (
                :userId, :yearValue, :monthValue, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
            ) AS new
            ON DUPLICATE KEY UPDATE
                total_sales = new.total_sales,
                total_fee = new.total_fee,
                total_vat = new.total_vat,
                total_refund = new.total_refund,
                total_settlement = new.total_settlement
            """, nativeQuery = true)
        void upsertMonthly(
                @Param("userId") Long userId,
                @Param("yearValue") Short yearValue,
                @Param("monthValue") Byte monthValue,
                @Param("totalSales") BigDecimal totalSales,
                @Param("totalFee") BigDecimal totalFee,
                @Param("totalVat") BigDecimal totalVat,
                @Param("totalRefund") BigDecimal totalRefund,
                @Param("totalSettlement") BigDecimal totalSettlement
        );

        //
        @Query("""
        SELECT COUNT(m)
        FROM MonthlySettlement m
          WHERE m.userId = :userId
          AND (
            m.year > :startYear
            OR (m.year = :startYear AND m.month >= :startMonth)
          )
          AND (
            m.year < :endYear
            OR (m.year = :endYear AND m.month <= :endMonth)
          )
        """)
        long countByRange(
                @Param("userId") Long userId,
                @Param("startYear") Short startYear,
                @Param("startMonth") Byte startMonth,
                @Param("endYear") Short endYear,
                @Param("endMonth") Byte endMonth
        );

}
