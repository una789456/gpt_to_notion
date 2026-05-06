package com.gptToNotion.controller;

import com.gptToNotion.dto.NotionDatabaseQueryRequest;
import com.gptToNotion.dto.NotionDatabaseSummary;
import com.gptToNotion.dto.NotionPageCreateRequest;
import com.gptToNotion.dto.NotionPageCreateResponse;
import com.gptToNotion.dto.NotionQueryResult;
import com.gptToNotion.dto.NotionSaveRequest;
import com.gptToNotion.dto.NotionSearchRequest;
import com.gptToNotion.dto.NotionSearchResponse;
import com.gptToNotion.service.NotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notion API 엔드포인트 컨트롤러
 * - 검색, DB 조회/질의, 페이지 생성, ChatGPT 저장 라우팅 기능
 */
@RestController
@RequestMapping("/api/notion")
@Tag(name = "Notion", description = "Notion 데이터베이스 검색/생성 API")
public class NotionController {

    private final NotionService notionService;

    /** Notion 컨트롤러 생성자 */
    public NotionController(NotionService notionService) {
        this.notionService = notionService;
    }

    /**
     * 사용 가능한 데이터베이스 목록 조회 엔드포인트 메소드
     * - `/search` 우선 조회 후 설정값 fallback 반환 기능
     */
    @Operation(summary = "사용 가능한 Notion DB 목록 조회", description = "Notion /search로 DB 목록을 가져오고, 없으면 설정값을 반환")
    @GetMapping(path = "/databases", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NotionDatabaseSummary> listDatabases() {
        return notionService.listDatabases();
    }

    /**
     * Notion 데이터베이스 질의 엔드포인트 메소드
     * - 요청 바디 생략 시 안전 기본값 적용 기능
     */
    @Operation(summary = "데이터베이스 질의", description = "Notion /databases/{id}/query 호출")
    @PostMapping(path = "/databases/{databaseId}/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public NotionQueryResult queryDatabase(
            @PathVariable String databaseId,
            @RequestBody(required = false) NotionDatabaseQueryRequest request
    ) {
        // body 생략 대응 안전 기본값
        NotionDatabaseQueryRequest safeRequest = request == null
                ? new NotionDatabaseQueryRequest(null, null, null, null)
                : request;

        return notionService.queryDatabase(databaseId, safeRequest);
    }

    /** 지정 데이터베이스 새 페이지 생성 엔드포인트 메소드 */
    @Operation(summary = "페이지 생성", description = "지정한 데이터베이스에 페이지 생성")
    @PostMapping(path = "/databases/{databaseId}/pages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public NotionPageCreateResponse createPage(
            @PathVariable String databaseId,
            @Valid @RequestBody NotionPageCreateRequest request
    ) {
        return notionService.createPage(databaseId, request);
    }

    /**
     * ChatGPT Q/A 저장 엔드포인트 메소드
     * - 제목/본문/속성 조합 후 페이지 생성 서비스 위임 기능
     */
    @Operation(summary = "ChatGPT 저장", description = "ChatGPT Q/A를 Notion 페이지로 저장")
    @PostMapping(path = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public NotionPageCreateResponse saveChatGpt(
            @Valid @RequestBody NotionSaveRequest request
    ) {
        return notionService.saveChatGpt(request);
    }

    /**
     * Notion 검색 엔드포인트 메소드
     * - 요청 바디 생략 시 기본 검색 요청 적용 기능
     */
    @Operation(summary = "검색", description = "Notion /search 호출")
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public NotionSearchResponse search(
            @Valid @RequestBody(required = false) NotionSearchRequest request
    ) {
        // body 생략 대응 기본 요청 객체
        NotionSearchRequest safeRequest = request == null
                ? new NotionSearchRequest(null, null, null, null, null)
                : request;
        return notionService.search(safeRequest);
    }

    /**
     * 특정 데이터베이스 메타데이터 조회 엔드포인트 메소드
     * - 속성명/타입/옵션 포함 원본 Map 반환 기능
     */
    @Operation(summary = "데이터베이스 속성 조회", description = "Notion /databases/{id} 호출로 속성 메타데이터 반환")
    @GetMapping(path = "/databases/{databaseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getDatabase(@PathVariable String databaseId) {
        return notionService.getDatabase(databaseId);
    }
}
