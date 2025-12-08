package com.homesweet.homesweetback.domain.settlement.dto.response;

public record SettlementStatsDto(
        Long totalCount,
        Long completedCount
){
    public static final SettlementStatsDto EMPTY = new SettlementStatsDto(0L, 0L);
}