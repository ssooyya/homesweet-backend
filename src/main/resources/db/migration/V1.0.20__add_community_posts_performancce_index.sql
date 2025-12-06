-- ================================================================
-- V1.0.20 - Community Posts 성능 최적화 인덱스 추가
-- ================================================================
-- 목적: 게시글 목록 조회 성능 개선
-- 대상: community_posts 테이블
-- 효과: Full Table Scan 제거 (102K rows → 51K rows)
--       응답 시간 예상 개선: 5.8초 → 0.1초 이하
-- ================================================================

-- 복합 인덱스 추가: is_deleted + created_at (DESC)
-- 1. is_deleted = false 조건 필터링
-- 2. created_at DESC 정렬 활용
--
-- 주의: 인덱스가 이미 존재하면 스킵 (중복 생성 방지)
SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'community_posts'
      AND index_name = 'idx_community_posts_deleted_created'
);

SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_community_posts_deleted_created ON community_posts(is_deleted, created_at DESC)',
    'SELECT "Index already exists, skipping creation" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 인덱스 생성 검증
-- EXPLAIN SELECT * FROM community_posts
-- WHERE is_deleted = false
-- ORDER BY created_at DESC
-- LIMIT 10;
--
-- 예상 결과:
-- - type: ref (기존 ALL에서 개선)
-- - key: idx_community_posts_deleted_created
-- - rows: ~51,266 (기존 102,532에서 50% 감소)
