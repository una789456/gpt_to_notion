package com.gptToNotion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notion 페이지 생성 응답 DTO.
 *
 * @param object Notion object 타입
 * @param id 생성된 페이지 ID
 * @param createdTime 생성 시각(ISO-8601)
 * @param url 생성된 페이지 URL
 */
public record NotionPageCreateResponse(
        String object,
        String id,
        @JsonProperty("created_time")
        String createdTime,
        String url
) {
}
