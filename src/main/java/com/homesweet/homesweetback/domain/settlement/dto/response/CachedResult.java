package com.homesweet.homesweetback.domain.settlement.dto.response;

import java.util.List;

// monthly
public record CachedResult<T>(
     List<T> content,
     long totalCount
) {
    public static <T> CachedResult<T> empty() {
        return new CachedResult<>(List.of(), 0L);
    }
}
