package com.gptToNotion.service;

import com.gptToNotion.client.NotionApiClient;
import com.gptToNotion.config.NotionDatabaseOption;
import com.gptToNotion.config.NotionProperties;
import com.gptToNotion.dto.NotionDatabaseQueryRequest;
import com.gptToNotion.dto.NotionDatabaseQueryResponse;
import com.gptToNotion.dto.NotionPageCreateRequest;
import com.gptToNotion.dto.NotionPageCreateResponse;
import com.gptToNotion.dto.NotionQueryResult;
import com.gptToNotion.dto.NotionSaveRequest;
import com.gptToNotion.dto.NotionSearchRequest;
import com.gptToNotion.dto.NotionSearchResponse;
import com.gptToNotion.dto.NotionDatabaseSummary;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Notion 비즈니스 로직 처리 서비스
 * - API 클라이언트 호출, 결과 평가, 설정 기반 fallback 처리 기능
 */
@Service
public class NotionService {

    private final NotionApiClient notionApiClient;
    private final NotionActionEvaluator notionActionEvaluator;
    private final NotionProperties notionProperties;
    private final NotionSaveRequestAssembler notionSaveRequestAssembler;

    /**
     * Notion 서비스 의존성 주입 생성자
     * - {@code notionApiClient}: Notion HTTP 통신 처리 의존성
     * - {@code notionActionEvaluator}: 조회 결과 액션 문자열 해석 의존성
     * - {@code notionProperties}: fallback 데이터베이스 목록 제공 의존성
     * - {@code notionSaveRequestAssembler}: ChatGPT 저장 요청 조립 의존성
     */
    public NotionService(NotionApiClient notionApiClient, NotionActionEvaluator notionActionEvaluator,
                         NotionProperties notionProperties,
                         NotionSaveRequestAssembler notionSaveRequestAssembler) {
        this.notionApiClient = notionApiClient;
        this.notionActionEvaluator = notionActionEvaluator;
        this.notionProperties = notionProperties;
        this.notionSaveRequestAssembler = notionSaveRequestAssembler;
    }

    /** 데이터베이스 조회 후 액션 문자열과 함께 반환 메소드 */
    public NotionQueryResult queryDatabase(String databaseId, NotionDatabaseQueryRequest request) {
        NotionDatabaseQueryResponse response = notionApiClient.queryDatabase(databaseId, request);
        String action = notionActionEvaluator.evaluate(response);

        return new NotionQueryResult(action, response);
    }

    /** 지정 데이터베이스 새 페이지 생성 메소드 */
    public NotionPageCreateResponse createPage(String databaseId, NotionPageCreateRequest request) {
        return notionApiClient.createPage(databaseId, request);
    }

    /**
     * ChatGPT 저장 요청 처리 메소드
     * - 본문 내 `properties:` JSON 추출 및 page properties 병합 기능
     * - property 블록 제거 후 페이지 생성 위임 기능
     */
    public NotionPageCreateResponse saveChatGpt(NotionSaveRequest request) {
        NotionPageCreateRequest pageRequest = notionSaveRequestAssembler.assemble(request);
        return createPage(request.databaseId().trim(), pageRequest);
    }

    /** Notion 검색 API 호출 메소드 */
    public NotionSearchResponse search(NotionSearchRequest request) {
        return notionApiClient.search(request);
    }

    /** 데이터베이스 메타데이터 원본 맵 반환 메소드 */
    public Map<String, Object> getDatabase(String databaseId) {
        return notionApiClient.getDatabase(databaseId);
    }

    /**
     * 사용 가능한 데이터베이스 목록 조회 메소드
     * - Notion `/search` 우선 조회 기능
     * - 빈 결과 또는 오류 시 application 설정값 fallback 반환 기능
     */
    public List<NotionDatabaseSummary> listDatabases() {
        try {
            // object=database 필터 기반 DB 엔트리 검색
            NotionSearchRequest req = new NotionSearchRequest(
                    null,
                    new NotionSearchRequest.NotionSearchFilter("database", "object"),
                    null,
                    100,
                    null
            );
            NotionSearchResponse resp = notionApiClient.search(req); // Notion /search 원본 응답
            List<Map<String, Object>> results = resp.results(); // 원본 result 배열
            List<NotionDatabaseSummary> list = results == null ? List.of() : results.stream()
                    .map(this::toSummary)
                    .filter(s -> s != null && s.id() != null && !s.id().isBlank())
                    .toList();
            if (!list.isEmpty()) return list;
        } catch (Exception ignored) { // API 장애/권한 오류 시 설정 fallback 경로
            // 설정 목록 fallback 경로
        }

        List<NotionDatabaseOption> configured = notionProperties.databases();
        if (configured == null) return Collections.emptyList();
        return configured.stream()
                .map(opt -> new NotionDatabaseSummary(opt.id(), opt.label()))
                .toList();
    }

    /**
     * Notion 검색 결과 단건 요약 DTO 변환 메소드
     * - id 문자열 변환 기능
     * - label title/name 우선순위 추출 기능
     */
    private NotionDatabaseSummary toSummary(Map<String, Object> raw) {
        if (raw == null) return null;
        Object id = raw.get("id");
        String title = extractTitle(raw);
        return new NotionDatabaseSummary(id == null ? null : id.toString(), title);
    }

    /**
     * Notion 데이터베이스 표시용 제목 추출 메소드
     * - 우선순위: {@code title[0].plain_text} -> {@code name} -> 빈 문자열
     * - title 배열 비어 있는 응답 대응 fallback 기능
     */
    @SuppressWarnings("unchecked")
    private String extractTitle(Map<String, Object> raw) {
        Object titleObj = raw.get("title");
        if (titleObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                Object plain = map.get("plain_text");
                if (plain != null) return plain.toString();
            }
        }
        // name property fallback 경로
        Object name = raw.get("name");
        return name == null ? "" : name.toString();
    }
}
