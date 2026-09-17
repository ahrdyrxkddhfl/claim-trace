-- =============================================================
-- PostgreSQL 전용 스키마 보강
--
-- Hibernate 의 ddl-auto 가 테이블을 만든 뒤에 실행된다.
-- JPA 어노테이션으로 표현할 수 없는 제약만 여기에 둔다.
-- =============================================================

-- INV-12 — 항목당 is_current = true 인 판정은 정확히 1건.
--
-- 부분 UNIQUE 인덱스는 조건을 만족하는 행들 사이에서만 유일성을 요구한다.
-- 대체된 판정은 is_current = false 라 인덱스에 포함되지 않으므로, 한 항목에
-- 판정 이력이 몇 건 쌓이든 현재 판정은 1건으로 강제된다(D-6 과 양립한다).
--
-- H2 는 이 문법을 지원하지 않아 애플리케이션 레벨(항목 행 배타 잠금)로만
-- 보장한다. PostgreSQL 에서는 그 잠금을 우회하더라도 DB 가 거부한다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_reviews_current
    ON reviews (claim_item_id)
    WHERE is_current;
