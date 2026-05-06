package com.gptToNotion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Notion 데이터베이스 질의 응답 DTO.
 *
 * @param object Notion object 타입 문자열
 * @param results 조회된 페이지/데이터베이스 엔트리 원본 목록
 * @param nextCursor 다음 페이지 커서(없으면 null)
 * @param hasMore 다음 페이지 존재 여부
 */
public record NotionDatabaseQueryResponse(
        String object,
        List<Map<String, Object>> results,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {
}
