package com.gptToNotion.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Notion 페이지 생성용 내부 요청 DTO.
 *
 * @param title 페이지 제목(필수)
 * @param content 페이지 본문(필수, markdown 유사 텍스트 허용)
 * @param properties 추가 Notion 속성 payload(선택)
 */
public record NotionPageCreateRequest(
        @NotBlank(message = "title is required")
        String title,
        @NotBlank(message = "content is required")
        String content,
        Map<String, Object> properties
) {
}
