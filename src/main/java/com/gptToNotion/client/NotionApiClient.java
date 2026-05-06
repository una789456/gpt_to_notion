package com.gptToNotion.client;

import com.gptToNotion.config.NotionProperties;
import com.gptToNotion.dto.NotionDatabaseQueryRequest;
import com.gptToNotion.dto.NotionDatabaseQueryResponse;
import com.gptToNotion.dto.NotionPageCreateRequest;
import com.gptToNotion.dto.NotionPageCreateResponse;
import com.gptToNotion.dto.NotionSearchRequest;
import com.gptToNotion.dto.NotionSearchResponse;
import com.gptToNotion.dto.NotionSearchRequest.NotionSearchFilter;
import com.gptToNotion.dto.NotionSearchRequest.NotionSearchSort;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Notion HTTP 통신 클라이언트
 * - Notion API 엔드포인트 URL, 버전, 토큰 주입 기능
 * - RestTemplate 기반 요청/응답 전송 기능
 */
@Component
public class NotionApiClient {

    private final NotionProperties properties;
    private final RestTemplate restTemplate;
    private final NotionPagePayloadFactory notionPagePayloadFactory;

    /**
     * Notion API 클라이언트 생성자
     * - PATCH 요청 지원 request factory 설정 기능
     * - 연결/읽기 타임아웃 설정 기능
     */
    public NotionApiClient(
            NotionProperties properties,
            RestTemplateBuilder restTemplateBuilder,
            NotionPagePayloadFactory notionPagePayloadFactory
    ) {
        this.properties = properties;
        this.notionPagePayloadFactory = notionPagePayloadFactory;
        this.restTemplate = restTemplateBuilder
                .requestFactory(HttpComponentsClientHttpRequestFactory.class) // PATCH 지원 설정
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 데이터베이스 질의 API 호출 메소드
     * - 필터/정렬/페이지네이션 전달 기능
     * - 응답 바디 누락 시 예외 발생 기능
     */
    public NotionDatabaseQueryResponse queryDatabase(String databaseId, NotionDatabaseQueryRequest request) {
        String url = String.format("%s/databases/%s/query", properties.baseUrl(), databaseId); // DB query 엔드포인트
        HttpHeaders headers = defaultHeaders(); // 인증/버전 포함 공통 헤더

        HttpEntity<NotionDatabaseQueryRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<NotionDatabaseQueryResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                NotionDatabaseQueryResponse.class
        );

        return Objects.requireNonNull(response.getBody(), "Notion response body is missing");
    }

    /**
     * 페이지 생성 API 호출 메소드
     * - page payload 조립 결과 전송 기능
     * - 100개 초과 children append 처리 기능
     */
    public NotionPageCreateResponse createPage(String databaseId, NotionPageCreateRequest request) {
        String url = String.format("%s/pages", properties.baseUrl());
        HttpHeaders headers = defaultHeaders();
        NotionPagePayloadFactory.NotionCreatePagePayload payload =
                notionPagePayloadFactory.build(databaseId, request);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload.body(), headers);
        ResponseEntity<NotionPageCreateResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                NotionPageCreateResponse.class
        );

        NotionPageCreateResponse created = Objects.requireNonNull(response.getBody(), "Notion response body is missing");

        if (!payload.remainingChildren().isEmpty()) {
            appendChildren(created.id(), payload.remainingChildren());
        }

        return created;
    }

    /**
     * 데이터베이스 메타데이터 조회 메소드
     * - 속성 정의 포함 원본 맵 반환 기능
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDatabase(String databaseId) {
        String url = String.format("%s/databases/%s", properties.baseUrl(), databaseId);
        HttpHeaders headers = defaultHeaders();

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
        );

        return (Map<String, Object>) Objects.requireNonNull(response.getBody(), "Notion response body is missing");
    }

    /**
     * Notion 검색 API 호출 메소드
     * - null 또는 공백 필드 제거 후 검색 payload 전송 기능
     */
    public NotionSearchResponse search(NotionSearchRequest request) {
        String url = String.format("%s/search", properties.baseUrl());
        HttpHeaders headers = defaultHeaders();

        Map<String, Object> body = toSearchBody(request); // null/blank 제거 검색 페이로드

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<NotionSearchResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                NotionSearchResponse.class
        );

        return Objects.requireNonNull(response.getBody(), "Notion response body is missing");
    }

    /** `/blocks/{block_id}/children` 기준 추가 children append 메소드 */
    private void appendChildren(String pageId, List<Map<String, Object>> blocks) {
        if (pageId == null || pageId.isBlank() || blocks == null || blocks.isEmpty()) return;

        // 100개 단위 chunk 전송 반복
        IntStream.iterate(0, i -> i < blocks.size(), i -> i + 100).forEach(start -> {
            List<Map<String, Object>> chunk = blocks.subList(start, Math.min(start + 100, blocks.size()));

            Map<String, Object> body = Map.of("children", chunk);
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = String.format("%s/blocks/%s/children", properties.baseUrl(), pageId);
            restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);
        });
    }

    /**
     * 검색 DTO -> `/search` 요청 페이로드 변환 메소드
     * - 값 없는 필드 제외 기능
     */
    private Map<String, Object> toSearchBody(NotionSearchRequest request) {
        Map<String, Object> body = new HashMap<>();
        if (request == null) {
            return body;
        }

        if (request.query() != null && !request.query().isBlank()) {
            body.put("query", request.query());
        }

        NotionSearchFilter filter = request.filter();
        if (filter != null && filter.value() != null && filter.property() != null) {
            body.put("filter", Map.of(
                    "value", filter.value(),
                    "property", filter.property()
            ));
        }

        NotionSearchSort sort = request.sort();
        if (sort != null && sort.direction() != null && sort.timestamp() != null) {
            body.put("sort", Map.of(
                    "direction", sort.direction(),
                    "timestamp", sort.timestamp()
            ));
        }

        if (request.pageSize() != null) {
            body.put("page_size", request.pageSize());
        }
        if (request.startCursor() != null) {
            body.put("start_cursor", request.startCursor());
        }

        return body;
    }

    /**
     * Notion 요청 공통 헤더 생성 메소드
     * - `Authorization` 및 `Notion-Version` 헤더 설정 기능
     */
    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.token());
        headers.add("Notion-Version", properties.version());
        return headers;
    }
}
