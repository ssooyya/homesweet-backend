package com.homesweet.homesweetback.domain.settlement.dto.response;

public record SettlementStatsDto(
        Long totalCount,
        Long completedCount
){}