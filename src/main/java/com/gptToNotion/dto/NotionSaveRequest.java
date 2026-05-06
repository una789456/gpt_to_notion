package com.gptToNotion.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * ChatGPT 저장 API(`/api/notion/save`) 요청 DTO.
 *
 * @param databaseId 저장 대상 Notion DB ID(필수)
 * @param title 클라이언트가 강제 지정한 제목(있으면 최우선)
 * @param content 저장 본문(있으면 question/answer 합성 대신 사용)
 * @param titleCandidate 제목 후보(서비스에서 title 다음 우선순위로 사용)
 * @param question 질문 전문(본문 합성 시 사용)
 * @param answer 답변 전문(본문 합성 시 사용)
 * @param sourceUrl 원본 대화 URL(메타 정보)
 * @param capturedAt 캡처 시각(메타 정보)
 * @param properties 사용자 지정 Notion 속성 payload
 */
public record NotionSaveRequest(
        @NotBlank(message = "databaseId is required")
        String databaseId,
        String title,
        String content,
        String titleCandidate,
        String question,
        String answer,
        String sourceUrl,
        String capturedAt,
        Map<String, Object> properties
) {
}
