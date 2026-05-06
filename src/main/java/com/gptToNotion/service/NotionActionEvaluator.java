package com.gptToNotion.service;

import com.gptToNotion.dto.NotionDatabaseQueryResponse;

/**
 * DB 질의 응답 -> 후속 처리 힌트 문자열 해석 전략 인터페이스
 * - 구현체 교체 기반 결과 판정 기준 확장 기능
 */
public interface NotionActionEvaluator {

    /**
     * 후속 처리용 액션 문자열 변환 메소드
     * - 예: 결과 없음(NO_RESULTS), 결과 있음(RESULTS_FOUND)
     */
    String evaluate(NotionDatabaseQueryResponse response);
}
