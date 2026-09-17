-- =============================================================
-- CLAIM-TRACE 시드 데이터
--
-- 목적
--   화면 목업에 등장하는 상태를 그대로 재현해, 애플리케이션을 띄우자마자
--   심사 흐름 전체를 실행할 수 있게 한다.
--
-- 구성
--   청구 1 (CLM-2026-0908-00412) — 심사중. 항목 4건, 판정 0건.
--     여기서 판정 저장 · 근거 채택 · 확정 · 설명문 생성을 실행한다.
--   청구 2 (CLM-2026-0901-00187) — 확정 완료. 항목 1건, 판정 1건.
--     INV-7(확정된 청구의 근거는 변경 불가) 검증에 쓴다.
--
-- 주의
--   id 를 명시적으로 지정하므로 H2 의 IDENTITY 카운터가 1 에 머문다.
--   파일 끝에서 100 부터 재시작시키지 않으면 애플리케이션의 첫 INSERT 가
--   PK 충돌로 실패한다.
--
--   비밀번호 해시는 인증이 구현 범위 밖이라 검증되지 않는 더미 값이다.
--   file_url 도 실제로 접근 가능한 주소가 아니다.
-- =============================================================


-- ── 사내 사용자 ───────────────────────────────────────────────
INSERT INTO users (id, emp_no, password_hash, name, role, active, created_at) VALUES
(1, 'M2026043', '$2a$10$seedonlynotverified000000000000000000000000000000000000', '김영희', 'REVIEWER',       TRUE, '2026-03-02 09:00:00'),
(2, 'M2024117', '$2a$10$seedonlynotverified000000000000000000000000000000000000', '이도현', 'REVIEWER',       TRUE, '2024-07-15 09:00:00'),
(3, 'M2019011', '$2a$10$seedonlynotverified000000000000000000000000000000000000', '박준호', 'REVIEW_MANAGER', TRUE, '2019-01-07 09:00:00');


-- ── 청구인 ───────────────────────────────────────────────────
INSERT INTO customers (id, login_id, password_hash, name, birth_year, phone, created_at) VALUES
(1, 'chulsoo85', '$2a$10$seedonlynotverified000000000000000000000000000000000000', '김철수', 1985, '010-2841-7730', '2021-04-11 14:22:00');


-- ── 계약과 담보 ──────────────────────────────────────────────
INSERT INTO policies (id, customer_id, policy_no, product_name, started_on, ended_on, created_at) VALUES
(1, 1, 'POL-2021-8830142', '종합실손의료비보험 4세대', '2021-05-01', NULL, '2021-04-11 14:30:00');

INSERT INTO coverages (id, policy_id, name, type, limit_amount, annual_limit, deductible_rate, exemption_until) VALUES
(1, 1, '질병 급여 통원',   'DISEASE_COVERED',   250000, 180, 0.200, NULL),
(2, 1, '질병 비급여 통원', 'DISEASE_UNCOVERED', 200000,  50, 0.300, NULL),
(3, 1, '상해 급여 통원',   'INJURY_COVERED',    250000, 180, 0.200, NULL),
(4, 1, '상해 비급여 통원', 'INJURY_UNCOVERED',  200000,  50, 0.300, NULL);


-- ── 심사 룰 ──────────────────────────────────────────────────
-- R-0412 는 버전 2건을 두어 「룰을 수정해도 과거 근거는 변하지 않는다」를
-- 데이터로 보인다. 근거는 v4 를 가리키고, v3 은 비활성이지만 보존된다.
INSERT INTO rules (id, code, version, name, coverage_type, article_no, article_text, polarity, condition_expr, content_internal, content_customer, default_disclosure_level, active, author_id, change_note, created_at) VALUES
(1, 'R-0412', 3, '비급여 도수치료 시행 한도', 'DISEASE_UNCOVERED', '제12조 3항',
 '비급여 도수치료는 질병 치료 목적으로 시행된 경우에 한하여 연간 30회를 한도로 보상한다.',
 'NEGATIVE', 'quantity > 30',
 '연간 시행 한도 30회를 초과한 회차에 대해 보상하지 않는다.',
 '가입하신 상품은 도수치료를 연간 30회까지 보장합니다.',
 'CUSTOMER', FALSE, 3, NULL, '2024-11-04 10:12:00'),

(2, 'R-0412', 4, '비급여 도수치료 시행 한도', 'DISEASE_UNCOVERED', '제12조 3항',
 '비급여 도수치료는 질병 치료 목적으로 시행된 경우에 한하여 연간 30회를 한도로 보상하며, 동일 상병으로 1개월 내 10회를 초과하여 시행된 경우 의학적 타당성 확인을 거친다.',
 'NEGATIVE', 'quantity > 30 OR (monthly_quantity > 10)',
 '1개월 내 10회 초과 시행분은 의학적 타당성 확인 대상이다. 소견서 미제출 시 보상하지 않는다.',
 '한 달 안에 도수치료를 10회 넘게 받으신 경우, 치료가 필요했다는 의사 소견을 확인한 뒤 보험금을 드립니다.',
 'CUSTOMER', TRUE, 3, '월간 시행 횟수 조건과 의학적 타당성 확인 절차 추가', '2026-02-19 15:41:00'),

(3, 'R-0771', 1, '체외충격파치료 의학적 타당성', 'DISEASE_UNCOVERED', '제12조 7항',
 '체외충격파치료는 보존적 치료에 반응하지 않는 경우에 한하여 보상한다.',
 'NEGATIVE', 'procedure_code IN (''MM380'') AND prior_conservative_care = false',
 '선행 보존치료 기록이 확인되지 않는다.',
 '체외충격파치료는 물리치료 등 다른 치료를 먼저 받으신 뒤에도 증상이 남은 경우에 보장됩니다.',
 'CUSTOMER', TRUE, 3, NULL, '2025-06-30 11:05:00'),

(4, 'R-0203', 2, '급여 항목 자기부담률 적용', 'DISEASE_COVERED', '제9조 1항',
 '급여 항목은 본인부담금의 80퍼센트를 보상한다.',
 'POSITIVE', 'is_covered = true',
 '급여 항목으로 자기부담률 20퍼센트를 적용해 산정한다.',
 '건강보험이 적용되는 진료비는 본인부담금의 80퍼센트를 보험금으로 드립니다.',
 'CUSTOMER', TRUE, 3, '자기부담률 표기 명확화', '2025-01-08 09:20:00'),

(5, 'R-0908', 1, '진료 목적 상병 부합', 'DISEASE_UNCOVERED', '제5조 2항',
 '청구된 진료가 기재된 상병의 치료 목적에 부합하는 경우 보상한다.',
 'POSITIVE', 'diagnosis_matches = true',
 '상병코드 M54.5 와 시행 항목의 치료 목적이 부합한다.',
 '진단명과 받으신 치료의 목적이 서로 맞는 것으로 확인되었습니다.',
 'CUSTOMER', TRUE, 3, NULL, '2025-09-12 16:30:00');


-- ── 인적 개입 규칙 ───────────────────────────────────────────
-- conditions 의 field 는 claimedAmount / exclusionProbability / coverageType,
-- op 는 gte / lte / eq 만 지원한다. 모든 조건은 AND 로 결합된다.
INSERT INTO intervention_rules (id, code, name, conditions, required_intervention, approver_roles, active, author_id, created_at, updated_at) VALUES
(1, 'P-07', '비급여 고액 항목',
 '[{"field":"coverageType","op":"eq","value":"DISEASE_UNCOVERED"},{"field":"claimedAmount","op":"gte","value":300000}]',
 'DUAL_CHECK', '["REVIEW_MANAGER"]', TRUE, 3, '2026-01-15 10:00:00', NULL),

(2, 'P-03', '고위험 보상제외 확률',
 '[{"field":"exclusionProbability","op":"gte","value":0.8}]',
 'DUAL_CHECK', '["REVIEW_MANAGER"]', TRUE, 3, '2025-11-20 14:30:00', '2026-02-02 09:15:00'),

(3, 'P-11', '급여 항목 소액 자동처리 제외',
 '[{"field":"coverageType","op":"eq","value":"DISEASE_COVERED"},{"field":"claimedAmount","op":"lte","value":30000}]',
 'ESCALATION', '["REVIEW_MANAGER"]', FALSE, 3, '2025-08-01 11:00:00', NULL);


-- =============================================================
-- 청구 1 — CLM-2026-0908-00412 (심사중)
-- =============================================================

INSERT INTO claims (id, claim_no, customer_id, policy_id, status, hospital_name, diagnosis_code, treated_from, treated_to, customer_memo, assignee_id, assigned_at, received_at, decided_at) VALUES
(1, 'CLM-2026-0908-00412', 1, 1, 'UNDER_REVIEW', '서울정형외과의원', 'M54.5', '2026-08-03', '2026-08-29',
 '허리 통증으로 8월 한 달간 통원 치료를 받았습니다. 도수치료는 담당의 권유로 진행했습니다.',
 1, '2026-09-08 09:14:00', '2026-09-08 08:47:00', NULL);

INSERT INTO documents (id, claim_id, type, name, file_url, size_bytes, ocr_status, ocr_result, uploaded_at, ocr_done_at) VALUES
(1, 1, 'RECEIPT',      '진료비계산서_20260829.pdf',  '/files/seed/receipt-1.pdf',      284133, 'DONE', '{"items":4,"total":1284000}', '2026-09-08 08:47:00', '2026-09-08 08:49:00'),
(2, 1, 'DETAIL',       '진료비세부내역서_20260829.pdf', '/files/seed/detail-1.pdf',    511208, 'DONE', '{"lines":18}',               '2026-09-08 08:47:00', '2026-09-08 08:50:00'),
(3, 1, 'OPINION',      '소견서_20260812.jpg',        '/files/seed/opinion-1.jpg',     1042887, 'DONE', '{"text":"요추부 염좌 및 긴장"}', '2026-09-08 08:48:00', '2026-09-08 08:51:00');

INSERT INTO claim_items (id, claim_id, coverage_id, seq, name, procedure_code, is_covered, quantity, claimed_amount, source_document_id, created_at) VALUES
(1, 1, 1, 1, '진찰료',              'AA254', TRUE,   1,   24000, 1, '2026-09-08 08:49:00'),
(2, 1, 2, 2, '도수치료',            'MM301', FALSE, 12,  480000, 1, '2026-09-08 08:49:00'),
(3, 1, 2, 3, '체외충격파치료',      'MM380', FALSE,  6,  600000, 1, '2026-09-08 08:49:00'),
(4, 1, 1, 4, '방사선단순영상진단',  'HA401', TRUE,   3,  180000, 1, '2026-09-08 08:49:00');

INSERT INTO ai_recommendations (id, claim_item_id, model_name, model_version, exclusion_probability, recommendation, threshold, is_latest, created_at) VALUES
(1, 1, 'claim-risk', 'v2.3', 0.040, 'PAY',     0.300, TRUE, '2026-09-08 08:52:00'),
(2, 2, 'claim-risk', 'v2.3', 0.820, 'DENY',    0.300, TRUE, '2026-09-08 08:52:00'),
(3, 3, 'claim-risk', 'v2.3', 0.610, 'PARTIAL', 0.300, TRUE, '2026-09-08 08:52:00'),
(4, 4, 'claim-risk', 'v2.3', 0.110, 'PAY',     0.300, TRUE, '2026-09-08 08:52:00');

-- 근거. 전부 GENERATED(미검토) 상태로 두어 화면 10 의 채택·기각을 실행할 수 있게 한다.
INSERT INTO evidences (id, claim_item_id, rule_id, ai_recommendation_id, document_id, source, polarity, status, disclosure_level, content_internal, content_customer, contribution, target_amount, rejection_reason_type, rejection_note, decided_by, decided_at, created_at) VALUES
-- 항목 1 진찰료
(1, 1, 4, NULL, NULL, 'RULE', 'POSITIVE', 'GENERATED', 'CUSTOMER',
 '급여 항목으로 자기부담률 20퍼센트를 적용해 산정한다.',
 '건강보험이 적용되는 진료비는 본인부담금의 80퍼센트를 보험금으로 드립니다.',
 NULL, 19200, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

-- 항목 2 도수치료 — 이 프로젝트의 시연 지점. 부정 근거 3건, 긍정 근거 1건.
(2, 2, 2, NULL, NULL, 'RULE', 'NEGATIVE', 'GENERATED', 'CUSTOMER',
 '1개월 내 10회 초과 시행분은 의학적 타당성 확인 대상이다. 소견서 미제출 시 보상하지 않는다.',
 '한 달 안에 도수치료를 10회 넘게 받으신 경우, 치료가 필요했다는 의사 소견을 확인한 뒤 보험금을 드립니다.',
 NULL, 80000, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

(3, 2, NULL, 2, NULL, 'AI', 'NEGATIVE', 'GENERATED', 'INTERNAL',
 '시행 회차(12회)가 동일 상병 통원 건의 상위 5퍼센트 구간에 해당한다. 기여도 0.412.',
 NULL, 0.412, NULL, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

(4, 2, NULL, 2, NULL, 'AI', 'NEGATIVE', 'GENERATED', 'INTERNAL',
 '동일 의료기관의 도수치료 청구 집중도가 평균 대비 높다. 기여도 0.237.',
 NULL, 0.237, NULL, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

(5, 2, 5, NULL, 3, 'RULE', 'POSITIVE', 'GENERATED', 'CUSTOMER',
 '상병코드 M54.5 와 시행 항목의 치료 목적이 부합한다. 소견서 확인.',
 '진단명과 받으신 치료의 목적이 서로 맞는 것으로 확인되었습니다.',
 NULL, NULL, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

-- 항목 3 체외충격파치료
(6, 3, 3, NULL, NULL, 'RULE', 'NEGATIVE', 'GENERATED', 'CUSTOMER',
 '선행 보존치료 기록이 확인되지 않는다.',
 '체외충격파치료는 물리치료 등 다른 치료를 먼저 받으신 뒤에도 증상이 남은 경우에 보장됩니다.',
 NULL, 300000, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

(7, 3, NULL, 3, NULL, 'AI', 'NEGATIVE', 'GENERATED', 'INTERNAL',
 '1회당 단가(100,000원)가 동일 항목 분포의 상위 구간에 해당한다. 기여도 0.318.',
 NULL, 0.318, NULL, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00'),

-- 항목 4 방사선단순영상진단
(8, 4, 4, NULL, NULL, 'RULE', 'POSITIVE', 'GENERATED', 'CUSTOMER',
 '급여 항목으로 자기부담률 20퍼센트를 적용해 산정한다.',
 '건강보험이 적용되는 진료비는 본인부담금의 80퍼센트를 보험금으로 드립니다.',
 NULL, 144000, NULL, NULL, NULL, NULL, '2026-09-08 08:53:00');


-- =============================================================
-- 청구 2 — CLM-2026-0901-00187 (확정 완료)
-- INV-7 검증용. 이 청구의 근거를 수정하려 하면 409 가 반환되어야 한다.
-- =============================================================

INSERT INTO claims (id, claim_no, customer_id, policy_id, status, hospital_name, diagnosis_code, treated_from, treated_to, customer_memo, assignee_id, assigned_at, received_at, decided_at) VALUES
(2, 'CLM-2026-0901-00187', 1, 1, 'DECIDED', '연세이비인후과의원', 'J30.1', '2026-08-14', '2026-08-14', NULL,
 1, '2026-09-01 10:30:00', '2026-09-01 10:02:00', '2026-09-02 14:18:00');

INSERT INTO documents (id, claim_id, type, name, file_url, size_bytes, ocr_status, ocr_result, uploaded_at, ocr_done_at) VALUES
(4, 2, 'RECEIPT', '진료비계산서_20260814.pdf', '/files/seed/receipt-2.pdf', 198420, 'DONE', '{"items":1,"total":38000}', '2026-09-01 10:02:00', '2026-09-01 10:04:00');

INSERT INTO claim_items (id, claim_id, coverage_id, seq, name, procedure_code, is_covered, quantity, claimed_amount, source_document_id, created_at) VALUES
(5, 2, 1, 1, '진찰료', 'AA254', TRUE, 1, 38000, 4, '2026-09-01 10:04:00');

INSERT INTO ai_recommendations (id, claim_item_id, model_name, model_version, exclusion_probability, recommendation, threshold, is_latest, created_at) VALUES
(5, 5, 'claim-risk', 'v2.3', 0.070, 'PAY', 0.300, TRUE, '2026-09-01 10:06:00');

INSERT INTO evidences (id, claim_item_id, rule_id, ai_recommendation_id, document_id, source, polarity, status, disclosure_level, content_internal, content_customer, contribution, target_amount, rejection_reason_type, rejection_note, decided_by, decided_at, created_at) VALUES
(9, 5, 4, NULL, NULL, 'RULE', 'POSITIVE', 'ADOPTED', 'CUSTOMER',
 '급여 항목으로 자기부담률 20퍼센트를 적용해 산정한다.',
 '건강보험이 적용되는 진료비는 본인부담금의 80퍼센트를 보험금으로 드립니다.',
 NULL, 30400, NULL, NULL, 1, '2026-09-02 14:10:00', '2026-09-01 10:07:00');

INSERT INTO reviews (id, claim_item_id, reviewer_id, decision, paid_amount, reason, is_current, superseded_by, decided_at) VALUES
(1, 5, 1, 'PAY', 30400, '급여 진찰료로 담보 한도 내이며 부정 근거가 확인되지 않는다. 자기부담률 20퍼센트를 적용해 산정했다.', TRUE, NULL, '2026-09-02 14:15:00');


-- 국소 설명서 신청.
-- 제공 기한 산정은 청구인의 신청 API 책임이고(INV-8, 기술서 5.5) 그 API 는
-- 고객 포털에 속해 구현 범위 밖이다. 초안 생성 엔드포인트는 이미 접수된
-- 설명서의 본문을 채우는 역할이므로, 신청이 완료된 상태를 시드로 만들어 둔다.
-- 기한은 신청일로부터 5영업일이다 (기술서 3.4 국소 설명 예시).
INSERT INTO explanations (id, claim_id, type, status, model_name, body, file_url, drafted_by, requested_at, due_date, provided_at) VALUES
(1, 1, 'LOCAL', 'REQUESTED', NULL, NULL, NULL, NULL, '2026-09-08 21:12:00', '2026-09-15', NULL);


-- =============================================================
-- IDENTITY 카운터 재시작
--
-- 위에서 id 를 명시적으로 넣었기 때문에 각 테이블의 자동 증가 값은 아직
-- 1 이다. 이 상태로 애플리케이션이 INSERT 하면 PK 충돌이 난다.
-- 시드가 쓰지 않은 구간부터 시작하도록 100 으로 올린다.
-- =============================================================
ALTER TABLE users                 ALTER COLUMN id RESTART WITH 100;
ALTER TABLE customers             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE policies              ALTER COLUMN id RESTART WITH 100;
ALTER TABLE coverages             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE rules                 ALTER COLUMN id RESTART WITH 100;
ALTER TABLE intervention_rules ALTER COLUMN id RESTART WITH 100;
ALTER TABLE claims                ALTER COLUMN id RESTART WITH 100;
ALTER TABLE documents             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE claim_items           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE ai_recommendations    ALTER COLUMN id RESTART WITH 100;
ALTER TABLE evidences             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE reviews               ALTER COLUMN id RESTART WITH 100;
ALTER TABLE interventions         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE explanations          ALTER COLUMN id RESTART WITH 100;
ALTER TABLE objections            ALTER COLUMN id RESTART WITH 100;
