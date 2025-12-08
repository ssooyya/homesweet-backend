package com.homesweet.homesweetback.domain.settlement.repository;

import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
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

public interface YearlySettlementRepository extends JpaRepository<YearlySettlement, Long> {
    // 특정 사용자의 기간별 집계 내역 조회
    List<YearlySettlement> findByUserIdAndYearBetweenOrderByYearDesc(
            Long userId, Short startDate, Short endDate
    );
    // 연별 집계 조회
    List<YearlySettlement> findByUserIdOrderByYearDesc(Long userId);

    @Query("SELECT y FROM YearlySettlement y WHERE y.userId = :userId")
    List<YearlySettlement> findByYearlySettlement(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(y)
        FROM YearlySettlement y
        WHERE y.userId = :userId
          AND y.year >= :fromYear
          AND y.year <  :toYearEx
    """)
    long countByRange(
            @Param("userId") Long userId,
            @Param("fromYear") Short fromYear,
            @Param("toYearEx") Short toYearEx
    );

    // 일별 집계 조회
    @Query(value = """
    SELECT y
    FROM YearlySettlement y
    WHERE y.userId = :userId
      AND y.year >= :fromYear
      AND y.year < :toYearEx
    ORDER BY y.year DESC
    """, countQuery = """
    SELECT COUNT(y)
    FROM YearlySettlement y
    WHERE y.userId = :userId
      AND y.year >= :fromYear
      AND y.year < :toYearEx
    """)
    Page<YearlySettlement> findByYearlySettlementByRange(
            @Param("userId") Long userId,
            @Param("fromYear") Short fromYear,
            @Param("toYearEx") Short toYearEx,
            Pageable pageable
    );
//    @Modifying
//    @Query(value = """
//        INSERT INTO yearly_settlements (
//            user_id, year_value, total_sales, total_fee, total_vat, total_refund, total_settlement
//        )
//        VALUES (
//            :userId, :yearValue, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
//        ) AS new
//        ON DUPLICATE KEY UPDATE
//            total_sales = new.total_sales,
//            total_fee = new.total_fee,
//            total_vat = new.total_vat,
//            total_refund = new.total_refund,
//            total_settlement = new.total_settlement
//        """, nativeQuery = true)
//    void upsertYearly(
//            @Param("userId") Long userId,
//            @Param("yearValue") Short yearValue,
//            @Param("totalSales") BigDecimal totalSales,
//            @Param("totalFee") BigDecimal totalFee,
//            @Param("totalVat") BigDecimal totalVat,
//            @Param("totalRefund") BigDecimal totalRefund,
//            @Param("totalSettlement") BigDecimal totalSettlement
//    );
}