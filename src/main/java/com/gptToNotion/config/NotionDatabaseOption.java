package com.gptToNotion.config;

/**
 * Notion DB 설정 엔트리(id + label) 보관용 레코드.
 */
public record NotionDatabaseOption(
        String id,
        String label
) {
}
