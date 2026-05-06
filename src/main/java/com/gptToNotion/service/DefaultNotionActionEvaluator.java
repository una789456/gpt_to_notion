package com.gptToNotion.service;

import com.gptToNotion.dto.NotionDatabaseQueryResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Query 결과 액션 문자열 기본 구현체
 * - 결과 없음 `NO_RESULTS` 반환 기능
 * - 결과 존재 `RESULTS_FOUND` 반환 기능
 */
@Component
@Primary
public class DefaultNotionActionEvaluator implements NotionActionEvaluator {

    /**
     * 결과 목록 존재 여부 기반 액션 결정 메소드
     * - null/빈 목록: NO_RESULTS
     * - 1건 이상 존재: RESULTS_FOUND
     */
    @Override
    public String evaluate(NotionDatabaseQueryResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "NO_RESULTS";
        }
        return "RESULTS_FOUND";
    }
}
