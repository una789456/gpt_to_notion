package com.gptToNotion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Notion `/databases/{id}/query` 요청 DTO.
 *
 * @param filter Notion filter 조건(JSON 객체 그대로 전달)
 * @param sorts 정렬 조건 리스트
 * @param pageSize 한 번에 조회할 결과 수(1~100 권장)
 * @param startCursor 다음 페이지 조회 커서
 */
public record NotionDatabaseQueryRequest(
        Map<String, Object> filter,
        List<Map<String, Object>> sorts,
        @JsonProperty("page_size") Integer pageSize,
        @JsonProperty("start_cursor") String startCursor
) {
}
