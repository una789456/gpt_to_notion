package com.gptToNotion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;

/**
 * Notion 검색 API(`/search`) 요청 DTO
 * - filter, sort 잘못된 값 조기 차단 기능
 */
public record NotionSearchRequest(
        String query,
        NotionSearchFilter filter,
        NotionSearchSort sort,
        @JsonProperty("page_size")
        @Min(value = 1, message = "page_size는 1 이상이어야 합니다")
        @Max(value = 100, message = "page_size는 100 이하여야 합니다")
        Integer pageSize,
        @JsonProperty("start_cursor")
        String startCursor
) {

    public NotionSearchRequest {
        // filter value/property 조합 조기 검증
        if (filter != null) {
            filter.validate();
        }
    }

    public record NotionSearchFilter(
            String value,   // "page" | "database"
            String property // "object"
    ) {
        void validate() {
            if (value == null || property == null) {
                return;
            }
            String normalizedValue = value.trim().toLowerCase(); // 공백/대소문자 정규화 비교값
            if (!normalizedValue.equals("page") && !normalizedValue.equals("database")) {
                throw new IllegalArgumentException("filter.value는 page 또는 database 이어야 합니다");
            }
        }
    }

    public record NotionSearchSort(
            String direction, // "ascending" | "descending"
            String timestamp  // e.g. "last_edited_time"
    ) {
        public NotionSearchSort {
            if (direction != null) {
                String normalized = direction.trim().toLowerCase(); // 허용 방향 비교용 정규화 값
                if (!Objects.equals(normalized, "ascending") && !Objects.equals(normalized, "descending")) {
                    throw new IllegalArgumentException("sort.direction은 ascending 또는 descending 이어야 합니다");
                }
            }
        }
    }
}
