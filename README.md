# GPT to Notion

Spring Boot 서비스로 Notion API 일부(데이터베이스 조회, 페이지 생성, 검색)를 프록시하며 Swagger UI로 테스트할 수 있습니다.

## 요구 사항
- Java 17+
- Gradle

## 실행
```bash
./gradlew bootRun
```
기본 포트는 `10210`입니다.

## 환경 변수
- `NOTION_TOKEN` (필수): Notion 통합 토큰

## API (REST)
- `POST /api/notion/databases/{databaseId}/query` — Notion 데이터베이스 쿼리 프록시
- `POST /api/notion/databases/{databaseId}/pages` — 데이터베이스에 페이지 생성
  - 본문: `{ title, content, properties }`
  - `properties`는 Notion API 스키마 그대로(Map) 전달하면 기본 제목과 병합되어 저장됩니다.
- `POST /api/notion/search` — Notion 검색 호출
- `POST /api/notion/save` — ChatGPT 저장 요청(스크립트 전용)
  - 본문: `{ databaseId, title, content, titleCandidate, question, answer, sourceUrl, capturedAt, properties }
  - `properties`를 포함하면 클라이언트가 전달한 Notion 속성이 그대로 `/pages` 호출에 반영됩니다.

## Notion Integration 연결(필수)
Notion API는 **해당 데이터베이스가 Integration에 공유**되어 있어야 접근할 수 있습니다.

1) Notion에서 대상 데이터베이스 페이지를 엽니다.
2) 우측 상단 `Share` 클릭
3) 상단 탭에서 **`Connections`(연결) / `Integrations`** 선택
4) 목록에서 **NOTION_TOKEN을 발급받은 Integration 이름**을 선택해 추가

`Connections` 탭이 안 보이면, 데이터베이스 전체 페이지가 아닌 **단일 페이지**에 있는 경우가 많습니다.
데이터베이스를 **전체 페이지(full page)**로 열고 다시 시도하세요.

### 예시: 페이지 생성
```bash
curl -X POST "http://localhost:10210/api/notion/databases/YOUR_DATABASE_ID/pages" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Gradle compileJava 에러 원인",
    "content": "Gradle 버전과 Spring Boot 플러그인 간 호환성 문제로 발생한 에러",
    "properties": {
      "상태": { "select": { "name": "Open" } },
      "중요도": { "multi_select": [ { "name": "High" } ] }
    }
  }'
```

### Swagger UI
애플리케이션 실행 후 접속:
- http://localhost:10210/swagger-ui/index.html

## Tampermonkey 스크립트로 ChatGPT → Notion 저장
경로: `scripts/tampermonkey/chatgpt-save-to-server.user.js`

1) 설치 및 설정
   - Tampermonkey에 스크립트를 추가한 뒤 아래 값을 자신의 서버로 수정합니다.
     - `SAVE_ENDPOINT` : `https://<host>/api/notion/save`
     - `DB_LIST_ENDPOINT` : `https://<host>/api/notion/databases`
     - `DB_INFO_ENDPOINT` : `https://<host>/api/notion/databases`
   - 필요하면 `CLIENT_TOKEN`을 서버에서 검증하도록 설정합니다.
   - `DATABASE_ID_OPTIONS` / `DEFAULT_DATABASE_ID`에 기본 DB를 적어두면 UI에서 선택/입력 가능합니다.

2) 동작 요약
   - ChatGPT 화면 우측 하단에 "Save (Notion)" 버튼과 DB 선택 패널이 생성됩니다.
   - 선택한 DB의 속성을 `/api/notion/databases/{id}`로 조회해 UI에 표시하고, 저장 요청 시 `properties`로 함께 서버에 전달합니다.
   - 드래그로 선택한 텍스트가 있으면 그것을 우선 저장, 없으면 최근 Q/A를 추출해 저장합니다.
   - `[notion]` 블록이 포함된 메시지는 블록 안의 `title/content/properties`를 우선 사용합니다.

3) GPT 프롬프트 가이드 (DB 속성에 맞게 응답 받기)
   - 스크립트가 DB 속성 목록을 텍스트로 붙여 보냅니다. 프롬프트 끝에 아래 규칙을 추가해 주세요:
```
[notion]
제목: <50자 이내>
내용: 본문을 단락/목록/코드블록으로 정리
properties: {
  "<속성이름>": <Notion 타입에 맞는 값>,
  ...
}

규칙:
- 위 properties 스키마에 맞춰 select/multi_select는 제공된 옵션에서만 선택.
- date는 ISO 8601(예: 2026-03-01), number는 숫자, checkbox는 true/false.
- 알 수 없는 값은 대화 문맥에서 추론하고 정말 없으면 ""(빈 문자열).
- 이 포맷 밖의 텍스트는 넣지 말 것.
```

4) 서버 동작
   - `/api/notion/save`는 전달된 `properties`를 기본 제목/키워드 속성과 병합해 Notion `/pages`에 그대로 전달합니다.
   - 별도 매핑 없이 Notion 스키마 형태를 그대로 받아들이므로, 클라이언트에서 올바른 형식을 보내야 합니다.
