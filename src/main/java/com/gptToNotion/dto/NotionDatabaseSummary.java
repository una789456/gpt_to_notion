package com.gptToNotion.dto;

/**
 * DB 선택 드롭다운에 표시할 최소 정보 요약 DTO.
 *
 * @param id Notion database id
 * @param label 사용자에게 보여줄 이름
 */
public record NotionDatabaseSummary(
        String id,
        String label
) {
}
