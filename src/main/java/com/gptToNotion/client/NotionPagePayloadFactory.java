package com.gptToNotion.client;

import com.gptToNotion.dto.NotionPageCreateRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 내부 페이지 생성 요청 -> Notion `/pages` payload 변환기
 * - 속성 정리, 본문 block 변환, children 100개 제한 분할 기능
 * - HTTP 클라이언트 전송 전 변환 책임 집중 기능
 */
@Component
public class NotionPagePayloadFactory {

    /** Notion 제목 기본 property 이름 */
    private static final String TITLE_PROPERTY_NAME = "제목";
    /** Notion `/pages` 1회 요청 최대 children 개수 */
    private static final int MAX_CHILDREN_PER_REQUEST = 100;

    /**
     * 페이지 생성 payload 조립 메소드
     * - 최초 `/pages` body 생성 기능
     * - 추가 append 대상 children 분리 기능
     *
     * @param databaseId 생성 대상 데이터베이스 ID
     * @param request 내부 페이지 생성 요청
     * @return 최초 생성 body 및 추가 children 묶음 반환
     */
    public NotionCreatePagePayload build(String databaseId, NotionPageCreateRequest request) {
        Map<String, Object> mergedProps = mergeProperties(request);
        List<Map<String, Object>> blocks = toContentBlocks(request.content());
        List<Map<String, Object>> firstChildren = blocks.size() > MAX_CHILDREN_PER_REQUEST
                ? List.copyOf(blocks.subList(0, MAX_CHILDREN_PER_REQUEST))
                : blocks;
        List<Map<String, Object>> remainingChildren = blocks.size() > MAX_CHILDREN_PER_REQUEST
                ? List.copyOf(blocks.subList(MAX_CHILDREN_PER_REQUEST, blocks.size()))
                : List.of();

        Map<String, Object> body = Map.of(
                "parent", Map.of("database_id", databaseId),
                "properties", mergedProps,
                "children", firstChildren
        );
        return new NotionCreatePagePayload(body, remainingChildren);
    }

    /**
     * 최종 properties 병합 메소드
     * - 추가 properties 정리 기능
     * - 제목 속성 기본 구조 보강 기능
     *
     * @param request 내부 페이지 생성 요청
     * @return 최종 property 맵 반환
     */
    private Map<String, Object> mergeProperties(NotionPageCreateRequest request) {
        Map<String, Object> merged = new HashMap<>();

        Map<String, Object> extra = request.properties() == null
                ? Map.of()
                : sanitizeProperties(request.properties());
        merged.putAll(extra);

        merged.putIfAbsent(TITLE_PROPERTY_NAME, Map.of(
                "title", List.of(Map.of("text", Map.of("content", request.title())))
        ));
        return merged;
    }

    /**
     * 입력 properties 정리 메소드
     * - 제목 속성 제외 기능
     * - 문자열/배열 편의 입력 자동 변환 기능
     *
     * @param props 클라이언트 전달 원본 properties
     * @return 정리된 Notion properties 반환
     */
    private Map<String, Object> sanitizeProperties(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            if (TITLE_PROPERTY_NAME.equals(key)) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                if (hasValidPayload(mapValue)) {
                    cleaned.put(key, value);
                }
                continue;
            }

            if (value instanceof List<?> listValue) {
                List<Map<String, String>> multiSelect = toMultiSelect(listValue.stream()
                        .map(Object::toString)
                        .toList());
                if (!multiSelect.isEmpty()) {
                    cleaned.put(key, Map.of("multi_select", multiSelect));
                }
                continue;
            }

            if (value instanceof String stringValue && !stringValue.isBlank()) {
                cleaned.put(key, Map.of(
                        "rich_text", List.of(Map.of("text", Map.of("content", stringValue)))
                ));
            }
        }
        return cleaned;
    }

    /**
     * Notion property payload 유효성 검사 메소드
     * - 메타데이터 전용 맵 제외 기능
     * - 실제 Notion 값 키 존재 여부 판별 기능
     *
     * @param mapValue 검사 대상 property 맵
     * @return 유효 payload 여부 반환
     */
    private boolean hasValidPayload(Map<?, ?> mapValue) {
        Object title = mapValue.get("title");
        if (title instanceof List<?>) {
            return true;
        }

        Object richText = mapValue.get("rich_text");
        if (richText instanceof List<?>) {
            return true;
        }

        Object number = mapValue.get("number");
        if (number instanceof Number) {
            return true;
        }

        Object url = mapValue.get("url");
        if (url instanceof String) {
            return true;
        }

        Object select = mapValue.get("select");
        if (select instanceof Map<?, ?>) {
            return true;
        }

        Object multiSelect = mapValue.get("multi_select");
        if (multiSelect instanceof List<?>) {
            return true;
        }

        Object people = mapValue.get("people");
        if (people instanceof List<?>) {
            return true;
        }

        Object email = mapValue.get("email");
        if (email instanceof String) {
            return true;
        }

        Object phone = mapValue.get("phone_number");
        if (phone instanceof String) {
            return true;
        }

        Object date = mapValue.get("date");
        if (date instanceof Map<?, ?>) {
            return true;
        }

        Object checkbox = mapValue.get("checkbox");
        if (checkbox instanceof Boolean) {
            return true;
        }

        Object relation = mapValue.get("relation");
        if (relation instanceof List<?>) {
            return true;
        }

        Object files = mapValue.get("files");
        if (files instanceof List<?>) {
            return true;
        }

        Object status = mapValue.get("status");
        return status instanceof Map<?, ?>;
    }

    /**
     * 문자열 배열 -> Notion {@code multi_select} 변환 메소드
     * - null/공백 제거 기능
     *
     * @param keywords 원본 문자열 목록
     * @return Notion multi_select 항목 목록 반환
     */
    private List<Map<String, String>> toMultiSelect(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        return keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> Map.of("name", value))
                .toList();
    }

    /**
     * 본문 문자열 -> Notion children block 변환 메소드
     * - paragraph/code/divider block 분기 기능
     * - 빈 줄 기준 문단 경계 처리 기능
     *
     * @param content 원본 본문
     * @return Notion block 목록 반환
     */
    private List<Map<String, Object>> toContentBlocks(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalized = content.replace("\r\n", "\n").trim();
        String[] lines = normalized.split("\n");
        List<Map<String, Object>> blocks = new java.util.ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean inCode = false;

        for (String raw : lines) {
            String line = raw.stripTrailing();
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inCode) {
                    addCodeBlock(blocks, code.toString());
                    code.setLength(0);
                    inCode = false;
                } else {
                    flushParagraph(blocks, paragraph);
                    inCode = true;
                }
                continue;
            }

            if (inCode) {
                code.append(raw).append('\n');
                continue;
            }

            if (trimmed.equals("---")) {
                flushParagraph(blocks, paragraph);
                blocks.add(Map.of("object", "block", "type", "divider", "divider", Map.of()));
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(blocks, paragraph);
                continue;
            }

            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(raw);
        }

        if (inCode) {
            addCodeBlock(blocks, code.toString());
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    /**
     * paragraph block 확정 메소드
     * - 비어 있는 버퍼 무시 기능
     * - 처리 후 버퍼 초기화 기능
     *
     * @param blocks 최종 결과 block 목록
     * @param paragraph 누적 문단 버퍼
     */
    private void flushParagraph(List<Map<String, Object>> blocks, StringBuilder paragraph) {
        String text = paragraph.toString().trim();
        if (text.isEmpty()) {
            paragraph.setLength(0);
            return;
        }

        blocks.add(Map.of(
                "object", "block",
                "type", "paragraph",
                "paragraph", Map.of(
                        "rich_text", List.of(Map.of("text", Map.of("content", text)))
                )
        ));
        paragraph.setLength(0);
    }

    /**
     * code block 추가 메소드
     * - 마지막 개행 제거 기능
     * - 빈 코드 block 생성 방지 기능
     *
     * @param blocks 최종 결과 block 목록
     * @param code 누적 코드 문자열
     */
    private void addCodeBlock(List<Map<String, Object>> blocks, String code) {
        String text = code.replaceFirst("\\n$", "");
        if (text.isEmpty()) {
            return;
        }

        blocks.add(Map.of(
                "object", "block",
                "type", "code",
                "code", Map.of(
                        "language", "plain text",
                        "rich_text", List.of(Map.of("text", Map.of("content", text)))
                )
        ));
    }

    /**
     * 최초 `/pages` body 및 추가 children 묶음 값 객체
     *
     * @param body 최초 `/pages` 생성 요청 body
     * @param remainingChildren 생성 후 추가 append 대상 children 목록
     */
    public record NotionCreatePagePayload(
            Map<String, Object> body,
            List<Map<String, Object>> remainingChildren
    ) {
    }
}
