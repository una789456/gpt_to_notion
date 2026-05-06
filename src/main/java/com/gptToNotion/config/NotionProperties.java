package com.gptToNotion.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * `application*.yaml`의 `notion.*` 설정을 바인딩하는 설정 레코드.
 *
 * @param baseUrl Notion API 기본 URL(예: https://api.notion.com/v1)
 * @param version Notion-Version 헤더 값(예: 2022-06-28)
 * @param token Notion 내부 통신용 시크릿 토큰(Bearer)
 * @param databases 실패 fallback 시 사용할 사전 등록 DB 목록
 */
@Validated
@ConfigurationProperties(prefix = "notion")
public record NotionProperties(
        String baseUrl,
        String version,
        String token,
        List<NotionDatabaseOption> databases
) {
}
