package com.gptToNotion.dto;

/**
 * DB 질의 결과에 "후속 액션 문자열"을 덧붙여 반환하는 래퍼 DTO.
 *
 * @param action 결과 해석 문자열(예: RESULTS_FOUND, NO_RESULTS)
 * @param response 원본 Notion 질의 응답
 */
public record NotionQueryResult(
        String action,
        NotionDatabaseQueryResponse response
) {
}
