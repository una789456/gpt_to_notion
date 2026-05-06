package com.gptToNotion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gptToNotion.dto.NotionPageCreateRequest;
import com.gptToNotion.dto.NotionSaveRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ChatGPT 저장 요청 -> Notion 페이지 생성 요청 변환 조립기
 * - 제목 결정, 본문 파싱, property 병합 규칙 캡슐화 기능
 * - 본문 내 {@code properties: {...}} 블록 추출 및 제거 기능
 * - 최종 {@link NotionPageCreateRequest} 생성 기능
 */
@Slf4j
@Component
public class NotionSaveRequestAssembler {

    /** 본문 JSON 블록 파싱용 공용 ObjectMapper */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** property JSON 블록 시작 식별용 예약 접두어 */
    private static final String PROPERTIES_PREFIX = "properties:";
    /** 제목 후보 전체 비어 있을 때 사용하는 기본 제목 */
    private static final String DEFAULT_TITLE = "ChatGPT Q&A";
    /** 기본 제목 채움 처리용 Notion 제목 속성명 */
    private static final String TITLE_PROPERTY_NAME = "제목";

    /**
     * 저장 요청 전체 조립 메소드
     * - 제목 우선순위 결정 기능
     * - 본문 확정 및 property 블록 제거 기능
     * - Notion 페이지 생성용 내부 DTO 반환 기능
     *
     * @param request ChatGPT 저장용 원본 요청
     * @return Notion 페이지 생성용 내부 요청 DTO 반환
     */
    public NotionPageCreateRequest assemble(NotionSaveRequest request) {
        String title = resolveTitle(request);
        String rawContent = resolveContent(request);
        Map<String, Object> propsFromContent = extractPropertiesFromContent(rawContent);
        String content = normalizeSeparatedBullets(removePropertiesFromContent(rawContent));
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content or answer is required");
        }

        Map<String, Object> mergedProps = mergeProperties(request.properties(), title, propsFromContent);
        return new NotionPageCreateRequest(title, content, mergedProps);
    }

    /**
     * 요청 properties 병합 메소드
     * - 요청 바디 properties 복사 기능
     * - 본문 추출 properties 우선 적용 기능
     * - 제목 속성 누락 시 기본 제목 구조 보강 기능
     *
     * @param requestProperties 클라이언트 직접 전송 properties
     * @param title 최종 결정 제목
     * @param propsFromContent 본문 파싱 properties
     * @return 최종 Notion property payload 반환
     */
    private Map<String, Object> mergeProperties(
            Map<String, Object> requestProperties,
            String title,
            Map<String, Object> propsFromContent
    ) {
        Map<String, Object> merged = new HashMap<>();
        if (requestProperties != null) {
            merged.putAll(requestProperties);
        }
        if (!propsFromContent.isEmpty()) {
            merged.putAll(propsFromContent);
        }
        if (!merged.containsKey(TITLE_PROPERTY_NAME)) {
            merged.put(TITLE_PROPERTY_NAME, Map.of(
                    "title", List.of(Map.of("text", Map.of("content", title)))
            ));
        }
        return merged;
    }

    /**
     * 본문 내 property JSON 블록 제거 메소드
     * - page properties 전용 정보 본문 제외 기능
     * - 중첩 괄호 고려 범위 계산 기능
     *
     * @param content 원본 본문
     * @return property 블록 제거 본문 반환
     */
    private String removePropertiesFromContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String normalized = content.replace("\r\n", "\n");
        String lower = normalized.toLowerCase();
        int keyIdx = lower.indexOf(PROPERTIES_PREFIX);
        if (keyIdx < 0) {
            return content;
        }

        int jsonStart = normalized.indexOf('{', keyIdx);
        if (jsonStart < 0) {
            return content;
        }

        int jsonEnd = findJsonObjectEnd(normalized, jsonStart);
        if (jsonEnd < 0) {
            return content;
        }

        return (normalized.substring(0, keyIdx) + normalized.substring(jsonEnd + 1))
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 분리 bullet 표현 정규화 메소드
     * - 줄바꿈 분리 bullet -> 한 줄 bullet 변환 기능
     * - Notion 저장용 목록 표현 안정화 기능
     * 예시 입력:
     * <pre>
     * -
     * 다음 작업
     * </pre>
     * 예시 출력: {@code - 다음 작업}
     *
     * @param content 정규화 전 본문
     * @return 목록 표현 정리 본문 반환
     */
    private String normalizeSeparatedBullets(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String normalized = content.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        List<String> output = new java.util.ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String current = lines[i];
            String trimmed = current.trim();
            if ("-".equals(trimmed) && i + 1 < lines.length) {
                String nextTrimmed = lines[i + 1].trim();
                if (!nextTrimmed.isEmpty() && !nextTrimmed.startsWith("- ")) {
                    output.add("- " + nextTrimmed);
                    i++;
                    continue;
                }
            }
            output.add(current);
        }

        return String.join("\n", output);
    }

    /**
     * 본문 property JSON 추출 메소드
     * - 첫 번째 JSON 객체 탐색 기능
     * - 파싱 실패 시 빈 맵 fallback 반환 기능
     *
     * @param content 원본 본문
     * @return 추출 property 맵 반환
     */
    private Map<String, Object> extractPropertiesFromContent(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }

        String normalized = content.replace("\r\n", "\n");
        String lower = normalized.toLowerCase();
        int keyIdx = lower.indexOf(PROPERTIES_PREFIX);
        if (keyIdx < 0) {
            return Map.of();
        }

        String tail = normalized.substring(keyIdx + PROPERTIES_PREFIX.length()).trim();
        if (tail.isEmpty()) {
            return Map.of();
        }

        String jsonCandidate = extractFirstJsonObject(tail);
        if (jsonCandidate == null || jsonCandidate.isBlank()) {
            return Map.of();
        }

        try {
            return OBJECT_MAPPER.readValue(jsonCandidate, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse properties from content", e);
            return Map.of();
        }
    }

    /**
     * 첫 번째 JSON 객체 문자열 추출 메소드
     * - 대응 닫는 중괄호 위치 계산 기능
     *
     * @param text 검색 대상 문자열
     * @return 첫 번째 JSON 객체 문자열 반환
     */
    private String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        int end = findJsonObjectEnd(text, start);
        if (end < 0) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * JSON 객체 종료 위치 탐색 메소드
     * - 문자열 리터럴/이스케이프 상태 추적 기능
     *
     * @param text 전체 문자열
     * @param start 첫 '{' 인덱스
     * @return 대응 닫는 중괄호 위치 반환
     */
    private int findJsonObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            }
            if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 최종 제목 결정 메소드
     * - 우선순위: {@code title -> titleCandidate -> question 첫 줄 -> answer 첫 줄 -> 기본값}
     *
     * @param request 저장 요청 원본
     * @return 최종 제목 문자열 반환
     */
    private String resolveTitle(NotionSaveRequest request) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }
        if (request.titleCandidate() != null && !request.titleCandidate().isBlank()) {
            return request.titleCandidate().trim();
        }
        if (request.question() != null && !request.question().isBlank()) {
            return firstLine(request.question());
        }
        if (request.answer() != null && !request.answer().isBlank()) {
            return firstLine(request.answer());
        }
        return DEFAULT_TITLE;
    }

    /**
     * 첫 줄 추출 메소드
     * - 제목 후보용 대표 줄 추출 기능
     * - 첫 줄 비어 있을 때 전체 문자열 fallback 반환 기능
     *
     * @param text 원본 텍스트
     * @return 첫 줄 또는 전체 문자열 반환
     */
    private String firstLine(String text) {
        String normalized = text.replace("\r\n", "\n").trim();
        int idx = normalized.indexOf('\n');
        if (idx == -1) {
            return normalized;
        }
        String line = normalized.substring(0, idx).trim();
        return line.isEmpty() ? normalized : line;
    }

    /**
     * 최종 본문 생성 메소드
     * - content 직접 사용 기능
     * - question/answer/sourceUrl/capturedAt 조합 기능
     *
     * @param request 저장 요청 원본
     * @return 최종 저장 본문 반환
     */
    private String resolveContent(NotionSaveRequest request) {
        if (request.content() != null && !request.content().isBlank()) {
            return request.content().trim();
        }

        StringBuilder builder = new StringBuilder();
        if (request.question() != null && !request.question().isBlank()) {
            builder.append("Q:\n").append(request.question().trim()).append("\n\n");
        }
        if (request.answer() != null && !request.answer().isBlank()) {
            builder.append("A:\n").append(request.answer().trim()).append("\n\n");
        }
        if (request.sourceUrl() != null && !request.sourceUrl().isBlank()) {
            builder.append("Source: ").append(request.sourceUrl().trim()).append("\n");
        }
        if (request.capturedAt() != null && !request.capturedAt().isBlank()) {
            builder.append("CapturedAt: ").append(request.capturedAt().trim()).append("\n");
        }
        return builder.toString().trim();
    }
}
