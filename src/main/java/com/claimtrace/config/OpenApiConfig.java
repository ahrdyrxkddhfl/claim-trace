package com.claimtrace.config;

import java.util.Map;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * OpenAPI 문서 구성.
 *
 * <p>두 가지를 한곳에서 처리한다. 컨트롤러마다 같은 어노테이션을 되풀이하면
 * 엔드포인트가 늘 때마다 빠뜨리는 곳이 생기고, 빠뜨린 곳은 문서가 조용히
 * 틀린 상태로 남는다.
 *
 * <ul>
 *   <li><b>오류 응답 스키마</b> — 2xx 가 아닌 모든 응답에 {@code ErrorResponse}
 *       를 물린다. 지정하지 않으면 springdoc 이 메서드 반환 타입으로 채워,
 *       400 응답 예시에 성공 응답의 모양이 실린다.</li>
 *   <li><b>경로 변수 설명</b> — {@code claimId}, {@code itemId} 같은 변수에
 *       설명과 예시 값을 붙인다. Swagger UI 의 입력칸에 그대로 나타난다.</li>
 * </ul>
 *
 * <p><b>오류 응답이 이 API 의 특징이다.</b> 응답 본문에 {@code invariant} 가
 * 실려, 어느 설계 규칙이 이 요청을 거부했는지 API 가 직접 밝힌다. 문서에서
 * 그것이 보이지 않으면 이 시스템을 처음 보는 사람은 평범한 400 으로 읽는다.
 */
@Configuration
public class OpenApiConfig {

    /** 오류 응답 스키마의 컴포넌트 이름. {@code $ref} 로 참조된다. */
    private static final String ERROR_SCHEMA = "ErrorResponse";

    /** 경로 변수 이름별 설명과 예시 값. */
    private static final Map<String, String[]> PATH_PARAMETER_DOCS = Map.of(
            "claimId", new String[] {"청구 식별자", "1"},
            "itemId", new String[] {"청구 항목 식별자", "2"},
            "evidenceId", new String[] {"근거 식별자", "2"},
            "explanationId", new String[] {"설명서 식별자", "1"},
            "ruleId", new String[] {"심사 룰 식별자", "2"});

    /**
     * 기본 생성자.
     */
    public OpenApiConfig() {
        // 상태를 갖지 않는다.
    }

    /**
     * 문서의 개요와 공통 스키마를 정의한다.
     *
     * <p>{@code ErrorResponse} 스키마를 손으로 조립한다. 레코드에서 자동
     * 추출하게 두면 필드 설명과 예시를 넣을 자리가 없고, 이 스키마는
     * 문서에서 가장 많이 읽히는 부분이라 설명이 필요하다.
     *
     * @return 구성된 OpenAPI 문서 골격
     */
    @Bean
    public OpenAPI claimTraceOpenApi() {
        Schema<?> errorSchema = new ObjectSchema()
                .description("""
                        공통 오류 응답. invariant 필드가 이 스키마의 특징이다.
                        어느 설계 불변조건이 요청을 거부했는지 API 가 직접 밝힌다.
                        형식 오류처럼 불변조건과 무관한 경우에는 null 이다.
                        """)
                .addProperty("code", new StringSchema()
                        .description("오류 코드")
                        .example("OVERRIDE_REASON_REQUIRED"))
                .addProperty("message", new StringSchema()
                        .description("사용자에게 보일 한국어 메시지")
                        .example("AI 권고와 다른 판정에는 오버라이드 사유가 필요합니다"))
                .addProperty("invariant", new StringSchema()
                        .description("위반된 불변조건 번호. 불변조건과 무관한 오류이면 null")
                        .example("INV-3"))
                .addProperty("details", new ObjectSchema()
                        .description("위반 상세. 구조는 오류 유형마다 다르다"))
                .addProperty("timestamp", new StringSchema()
                        .format("date-time")
                        .description("응답 생성 시각"));

        return new OpenAPI()
                .info(new Info()
                        .title("CLAIM-TRACE API")
                        .version("v1")
                        .description("""
                                보험금 지급심사 근거 추적 및 인적 개입 기록 시스템.

                                규제가 요구하는 "누가 무엇을 근거로 결정했는가"를 데이터 모델과
                                API 계약으로 표현한다. 설계 불변조건 13개 중 구현 범위에 드는
                                12개를 서버가 강제하며, 위반 시 응답의 invariant 필드에
                                어느 규칙이 거부했는지를 담는다.

                                인증은 구현 범위 밖이다. 행위자는 X-Actor-Id 헤더로 전달한다.
                                시드 사용자는 1(심사자 김영희), 2(심사자 이도현),
                                3(심사관리자 박준호)이다.
                                """))
                .components(new Components().addSchemas(ERROR_SCHEMA, errorSchema));
    }

    /**
     * 오류 응답에 공통 스키마를 물리고 경로 변수에 설명을 채운다.
     *
     * @return springdoc 이 각 오퍼레이션에 적용할 후처리기
     */
    @Bean
    public OperationCustomizer claimTraceOperationCustomizer() {
        return (operation, handlerMethod) -> {
            applyErrorSchema(operation);
            applyParameterDocs(operation);
            return operation;
        };
    }

    /**
     * 2xx 가 아닌 응답에 오류 스키마를 지정한다.
     *
     * @param operation 대상 오퍼레이션
     */
    private void applyErrorSchema(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        Content errorContent = new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)));

        operation.getResponses().forEach((statusCode, response) -> {
            if (statusCode.startsWith("2")) {
                return;
            }
            response.setContent(errorContent);
        });
    }

    /**
     * 경로 변수에 설명과 예시 값을 채운다.
     *
     * <p>이미 설명이 적혀 있으면 덮어쓰지 않는다. 개별 엔드포인트가 더
     * 구체적으로 설명할 여지를 남긴다.
     *
     * @param operation 대상 오퍼레이션
     */
    private void applyParameterDocs(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            String[] docs = PATH_PARAMETER_DOCS.get(parameter.getName());
            if (docs == null) {
                continue;
            }
            if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                parameter.setDescription(docs[0]);
            }
            if (parameter.getExample() == null) {
                parameter.setExample(docs[1]);
            }
        }
    }
}
