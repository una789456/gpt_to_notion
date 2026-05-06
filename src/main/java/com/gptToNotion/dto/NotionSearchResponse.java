package com.gptToNotion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Notion 검색 API(`/search`) 응답 DTO
 * - 결과 목록, next_cursor, has_more 축약 직렬화/역직렬화 기능
 */
public record NotionSearchResponse(
        String object,
        List<Map<String, Object>> results,
        @JsonProperty("next_cursor")
        String nextCursor,
        @JsonProperty("has_more")
        boolean hasMore
) {
}
