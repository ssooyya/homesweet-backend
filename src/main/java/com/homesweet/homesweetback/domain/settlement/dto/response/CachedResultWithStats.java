package com.homesweet.homesweetback.domain.settlement.dto.response;

import java.util.List;

public record CachedResultWithStats<T>(
     List<T> content,
     long totalCount,
     SettlementStatsDto stats
) {
    public static final SettlementStatsDto EMPTY_STATS = new SettlementStatsDto(0L, 0L);

    public static <T> CachedResultWithStats<T> empty() {
        return new CachedResultWithStats<>(List.of(), 0L, EMPTY_STATS);
    }
}
