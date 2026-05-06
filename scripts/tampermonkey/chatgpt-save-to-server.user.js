// ==UserScript==
// @name         ChatGPT -> Save to My Server Button
// @namespace    http://tampermonkey.net/
// @version      0.4
// @description  ChatGPT 화면 "Save (Notion)" 버튼 추가 및 최근 Q/A 서버 전송 스크립트
// @match        https://chatgpt.com/*
// @match        https://chat.openai.com/*
// @grant        GM_addStyle
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(() => {
  'use strict';

  // ---------------------- 개요 ----------------------
  // ChatGPT 화면 우측 하단 "Save (Notion)" 버튼 추가 스크립트
  // 최근 대화 또는 선택 텍스트를 사용자 서버 API로 전송하는 기능
  // `/api/notion/save`, `/api/notion/databases`, `/api/notion/databases/{id}` 사용 전제

  /**
   * ✅ 반드시 변경 대상 서버 API 기준 주소 설정 블록
   * - Tampermonkey 호출 대상 사용자 서버 주소 설정 기능
   * - Notion Secret 브라우저 노출 방지용 프록시 서버 전제
   * - 예시 값: https://my-domain.com/api/notion/save 형태
   */
  // 모든 API 경로의 기준 주소(환경 전환 시 이 값만 바꾸면 하위 엔드포인트가 함께 바뀜)
  const HOST = 'https://YOUR_SERVER_HOST'
  // 실제 저장 요청을 보내는 엔드포인트
  const SAVE_ENDPOINT = HOST + '/api/notion/save';
  // 사용 가능한 DB 목록(라벨+id) 조회 엔드포인트
  const DB_LIST_ENDPOINT = HOST + '/api/notion/databases';
  // 특정 DB의 속성 스키마 조회 엔드포인트(/:databaseId 형태로 사용)
  const DB_INFO_ENDPOINT = HOST + '/api/notion/databases';

  // GPT 프롬프트용: DB 속성 예시를 정리할 때 표시할 최대 항목 수
  const MAX_PROP_HINT = 10; // 프롬프트 길이 폭주 방지를 위한 상한

  /**
   * 선택형 클라이언트 토큰 설정 블록
   * - 서버 요청 헤더 동봉용 간단 토큰 설정 기능
   * - 무단 호출 감소 목적 게이트 용도
   * - 민감 시크릿 대체 용도 사용 금지
   */
  const CLIENT_TOKEN = ''; // 예: 'my-local-token'
  const CLIENT_TOKEN_HEADER = 'X-CLIENT-TOKEN'; // 서버에서 검사할 커스텀 헤더 이름

  /**
   * ✅ databaseId 입력/선택 설정 블록
   * - 사전 등록 옵션 기반 select 선택 기능
   * - 직접 ID 입력 기능
   * - localStorage 기반 마지막 입력값 유지 기능
   */
  const DATABASE_ID_OPTIONS = [
    // { label: '개인 노트', id: 'YOUR_DB_ID_1' },
    // { label: '업무 기록', id: 'YOUR_DB_ID_2' }
  ];
  const DEFAULT_DATABASE_ID = ''; // 스토리지에 값이 없을 때 사용할 1차 기본 DB
  const DB_STORAGE_KEY = 'tm_notion_database_id'; // localStorage 키(마지막 선택 DB 보존)
  /**
   * 주요 DOM id 상수 테이블
   * - 하드코딩 문자열 중복 제거 기능
   * - UI 구조 변경 시 수정 지점 축소 기능
   */
  const UI_IDS = {
    saveBtn: 'tm-notion-save-btn',
    toast: 'tm-notion-toast',
    panel: 'tm-notion-panel',
    dbSelect: 'tm-notion-db-select',
    dbInput: 'tm-notion-db-input',
    dbMeta: 'tm-notion-db-meta'
  };
  /**
   * 런타임 상태 저장소
   * - 새로고침 전까지 유지되는 메모리 상태 보관 기능
   * - 현재 DB 옵션 목록 캐시 보관 기능
   */
  const state = {
    cachedDatabaseOptions: []
  };

  /**
   * UI CSS 주입 블록
   * - GM_addStyle 기반 스타일 삽입 기능
   * - 우측 하단 버튼/토스트/패널 레이아웃 정의 기능
   */
  GM_addStyle(`
    /* 저장 버튼 */
    #tm-notion-save-btn {
      position: fixed;             /* 화면에 고정 */
      right: 16px;                 /* 오른쪽 여백 */
      bottom: 16px;                /* 아래 여백 */
      z-index: 2147483647;         /* 최상단에 보이도록 매우 큰 값 */
      padding: 10px 12px;
      border-radius: 12px;
      border: 1px solid rgba(0,0,0,.14);
      background: #fff;
      font-size: 13px;
      cursor: pointer;
      box-shadow: 0 6px 24px rgba(0,0,0,.12);
      user-select: none;           /* 텍스트 선택 방지 */
    }
    #tm-notion-save-btn:hover { background: #f7f7f7; }
    #tm-notion-save-btn:disabled { opacity: .55; cursor: not-allowed; }

    /* 토스트(상태 메시지) */
    #tm-notion-toast {
      position: fixed;
      right: 16px;
      bottom: 62px;                /* 버튼 위에 뜨도록 */
      z-index: 2147483647;
      max-width: 360px;
      padding: 8px 10px;
      border-radius: 10px;
      background: rgba(0,0,0,.78);
      color: #fff;
      font-size: 12px;
      display: none;               /* 기본은 숨김 */
      white-space: pre-wrap;       /* \n 줄바꿈 유지 */
      word-break: break-word;
    }

    /* 우측 하단 패널 (DB 선택 + 메타) */
    #tm-notion-panel {
      position: fixed;
      right: 16px;
      bottom: 72px;
      z-index: 2147483647;
      width: 200px;
      max-width: 88vw;
      background: #fff;
      border: 1px solid rgba(0,0,0,.12);
      border-radius: 14px;
      box-shadow: 0 10px 28px rgba(0,0,0,.16);
      padding: 12px 12px 10px;
      font-size: 13px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    #tm-notion-panel h4 {
      margin: 0 0 4px 0;
      font-size: 14px;
      font-weight: 700;
      color: #111827;
    }
    #tm-notion-panel .tm-field { display: flex; flex-direction: column; gap: 4px; }
    #tm-notion-panel label { font-weight: 600; color: #374151; }
    #tm-notion-panel select,
    #tm-notion-panel input {
      font-size: 13px;
      padding: 6px 8px;
      border-radius: 9px;
      border: 1px solid rgba(0,0,0,.16);
      outline: none;
    }
    #tm-notion-panel .tm-inline {
      display: flex;
      gap: 6px;
    }
    #tm-notion-panel .tm-inline > * { flex: 1; }
    #tm-notion-db-meta {
      border: 1px dashed rgba(0,0,0,.14);
      border-radius: 10px;
      padding: 8px 10px;
      min-height: 52px;
      max-height: 160px;
      overflow: auto;
      background: #fafafa;
      color: #111827;
      line-height: 1.4;
    }
    #tm-notion-db-meta .tm-meta-empty { color: #6b7280; }
    #tm-notion-db-meta ul { margin: 0; padding-left: 16px; }
    #tm-notion-db-meta li { margin: 2px 0; }
  `);

  /**
   * 토스트 메시지 표시 메소드
   * - 일정 시간 후 자동 숨김 기능
   */
  function toast(msg) {
    const el = getUiEl(UI_IDS.toast); // 상태 메시지를 띄울 단일 토스트 영역
    if (!el) return;
    el.textContent = msg;
    el.style.display = 'block';
    // 연속 호출 시 이전 hide 타이머를 먼저 지워야 최신 메시지가 충분히 노출됨
    clearTimeout(el.__t);
    el.__t = setTimeout(() => (el.style.display = 'none'), 2600);
  }

  /**
   * 텍스트 정규화 메소드
   * - 윈도우 개행 -> `\n` 통일 기능
   * - 과도한 연속 줄바꿈 축소 기능
   * - 앞뒤 공백 제거 기능
   */
  function normalizeText(t) {
    return (t || '')
      .replace(/\r\n/g, '\n')
      .replace(/\n{4,}/g, '\n\n\n')
      .trim();
  }

  /**
   * DOM 요소 조회 메소드
   * - `document.getElementById` 공통 래퍼 기능
   *
   * @param {string} id 조회 대상 DOM id
   * @returns {HTMLElement|null} 조회 결과 반환
   */
  function getUiEl(id) {
    return document.getElementById(id);
  }

  /**
   * 최종 요청 헤더 생성 메소드
   * - 기본 헤더 유지 기능
   * - 클라이언트 토큰 헤더 조건부 병합 기능
   *
   * @param {Record<string, string>} [baseHeaders={}] 기본 헤더 집합
   * @returns {Record<string, string>} 최종 요청 헤더 반환
   */
  function buildClientHeaders(baseHeaders = {}) {
    return CLIENT_TOKEN
      ? { ...baseHeaders, [CLIENT_TOKEN_HEADER]: CLIENT_TOKEN }
      : baseHeaders;
  }

  /**
   * 성공 상태 코드 판별 메소드
   *
   * @param {number} status HTTP 상태 코드
   * @returns {boolean} 성공 여부 반환
   */
  function isSuccessfulStatus(status) {
    return status >= 200 && status < 300;
  }

  /**
   * 안전 JSON 파싱 메소드
   * - 예외 대신 null 반환 기능
   * - 빈 문자열 입력 시 fallback JSON 사용 기능
   *
   * @param {string} text 파싱 대상 문자열
   * @param {string} fallbackValue 빈 문자열 대응 기본 JSON 문자열
   * @returns {any|null} 파싱 결과 반환
   */
  function tryParseJson(text, fallbackValue) {
    try {
      return JSON.parse(text || fallbackValue);
    } catch (e) {
      return null;
    }
  }

  /**
   * Tampermonkey XHR Promise 래핑 메소드
   * - `async/await` 사용용 비동기 래퍼 기능
   * - 공통 네트워크 오류/타임아웃 표준화 기능
   *
   * @param {object} options `GM_xmlhttpRequest` 옵션 객체
   * @returns {Promise<object>} Tampermonkey 응답 객체 반환
   */
  function gmRequest(options) {
    return new Promise((resolve, reject) => {
      GM_xmlhttpRequest({
        ...options,
        onload: resolve,
        onerror: () => reject(new Error('Network error')),
        ontimeout: () => reject(new Error('Request timeout'))
      });
    });
  }

  // ChatGPT DOM 텍스트 직렬화 시 문단 구분 기준 블록 태그 목록
  const BLOCK_TAGS = new Set([
    'P', 'DIV', 'SECTION', 'ARTICLE', 'MAIN', 'HEADER', 'FOOTER',
    'UL', 'OL', 'LI', 'PRE', 'BLOCKQUOTE', 'HR',
    'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
    'TABLE', 'THEAD', 'TBODY', 'TR', 'TD', 'TH'
  ]);

  /**
   * 문단 구분 블록 요소 판별 메소드
   *
   * @param {Element|null|undefined} el 검사 대상 DOM 요소
   * @returns {boolean} 블록 요소 여부 반환
   */
  function isBlock(el) {
    return el && el.nodeType === Node.ELEMENT_NODE && BLOCK_TAGS.has(el.tagName);
  }

  /**
   * DOM 노드 평문 직렬화 메소드
   * - TEXT/PRE/CODE/LI/HR/BR 분기 직렬화 기능
   * - UI 장식 노드 제외 기능
   *
   * @param {Node} node 변환 대상 노드
   * @param {string[]} out 직렬화 결과 누적 버퍼
   * @returns {void}
   */
  function serializeNode(node, out) {
    // 호출 측에서 null을 넣을 가능성에 대비한 가드
    if (!node) return;
    if (node.nodeType === Node.TEXT_NODE) {
      out.push(node.textContent || '');
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;

    const el = node;          // 이하부터 엘리먼트 전용 로직 사용
    const tag = el.tagName;   // 분기 비교 비용을 줄이기 위해 1회만 읽음

    // 버튼/아이콘은 본문 데이터가 아니라 UI 장식이므로 추출에서 제외
    if (tag === 'BUTTON' || tag === 'SVG' || tag === 'PATH') return;

    if (tag === 'HR') {
      out.push('\n---\n');
      return;
    }

    if (tag === 'BR') {
      out.push('\n');
      return;
    }

    if (tag === 'PRE') {
      const cmContent = el.querySelector('.cm-content'); // ChatGPT 코드블록 편집기 구조 대응
      const codeEl = el.querySelector('code');           // 일반 code 태그 fallback
      const codeText = (cmContent?.textContent || codeEl?.textContent || el.textContent || '')
        // 마지막 줄바꿈 1개만 제거해 fenced block 닫힘 형식 안정화
        .replace(/\n$/, '');
      out.push('\n```\n' + codeText + '\n```\n');
      return;
    }

    if (tag === 'CODE') {
      const codeText = (el.textContent || '').trim();
      if (codeText) out.push('`' + codeText + '`');
      return;
    }

    if (tag === 'LI') {
      out.push('\n- ');
      el.childNodes.forEach((child) => serializeNode(child, out));
      return;
    }

    const isBlockTag = isBlock(el);
    if (isBlockTag) out.push('\n');
    el.childNodes.forEach((child) => serializeNode(child, out));
    if (isBlockTag) out.push('\n');
  }

  /**
   * article 평문 텍스트 추출 메소드
   * - 자식 노드 순회 직렬화 기능
   * - 최종 normalizeText 정리 기능
   *
   * @param {HTMLElement} articleEl 메시지 article 요소
   * @returns {string} 정규화된 메시지 텍스트 반환
   */
  function extractArticleText(articleEl) {
    const out = []; // 문자열 누적 버퍼(문자열 직접 += 보다 중간 생성물 감소)
    articleEl.childNodes.forEach((child) => serializeNode(child, out));
    return normalizeText(out.join(''));
  }

  /**
   * [notion] 블록 파싱 메소드
   *
   * 기대 포맷 예시:
   * [notion]
   * 제목: ...
   * 키워드: a, b, c
   * 내용:
   * ...
   *
   * 파싱 규칙:
   * - 제목/키워드/내용 접두어 한글/영문 허용 기능
   * - 내용 블록 시작 이후 라인 누적 기능
   * - 제목 또는 내용 비어 있을 때 null 반환 기능
   *
   * @param {string} t 원본 텍스트
   * @returns {{title: string, keywords: string[], content: string}|null} 파싱 결과 반환
   */
  function parseNotionBlock(t) {
    const text = normalizeText(t); // 파싱 전 개행/앞뒤 공백 정리
    const tagIdx = text.toLowerCase().indexOf('[notion]'); // 태그 시작 위치(없으면 파싱 중단)
    if (tagIdx === -1) return null;

    const body = text.slice(tagIdx + '[notion]'.length).trim(); // 태그 이후 실제 본문만 분리
    const lines = body.split('\n'); // 라인 단위 상태머신 파싱용

    let title = '';          // "제목:" 값
    let keywordsLine = '';   // "키워드:" 원문(후처리로 배열 변환)
    let contentLines = [];   // 내용 본문 라인 누적 버퍼
    let inContent = false;   // "내용:" 이후 구간인지 나타내는 상태 플래그

    // 접두어 패턴: 다국어(한글/영문) 입력 모두 허용
    const titleRe = /^(제목|title)\s*:\s*/i;
    const keywordsRe = /^(키워드|keywords?)\s*:\s*/i;
    const contentRe = /^(내용|content)\s*:\s*/i;

    for (const raw of lines) {
      const line = raw.trim();
      // 내용 구간 진입 전의 빈 줄은 의미가 없어 건너뜀
      if (!line && !inContent) continue;

      if (titleRe.test(line)) {
        title = line.replace(titleRe, '').trim();
        inContent = false;
        continue;
      }
      if (keywordsRe.test(line)) {
        keywordsLine = line.replace(keywordsRe, '').trim();
        inContent = false;
        continue;
      }
      if (contentRe.test(line)) {
        // "내용: 첫 줄"처럼 같은 줄에 본문이 붙어있는 케이스 처리
        const rest = line.replace(contentRe, '').trim();
        if (rest) contentLines.push(rest);
        inContent = true;
        continue;
      }
      if (inContent) {
        contentLines.push(raw);
      }
    }

    // 쉼표 기준 분리 + 공백 제거 + 빈 항목 제거
    const keywords = keywordsLine
      ? keywordsLine.split(',').map((k) => k.trim()).filter(Boolean)
      : [];

    const content = normalizeText(contentLines.join('\n')); // 누적 라인을 다시 문자열로 합침

    if (!title || !content) return null;
    return { title, keywords, content };
  }

  /**
   * 메시지 역할 추정 메소드
   *
   * ⚠️ 참고:
   * - ChatGPT UI DOM 구조 변경 대응 휴리스틱 판별 기능
   * - 판별 실패 시 `unknown` 반환 기능
   */
  function detectRole(articleEl) {
    const txt = (articleEl?.innerText || '').trim(); // 가시 텍스트 기준으로 1차 판별
    if (!txt) return 'unknown';

    // 휴리스틱 #1: data-testid 속성에 user/assistant 힌트가 있는지 확인
    const roleHints = articleEl.querySelectorAll('[data-testid]'); // UI 내부 힌트 속성 탐색
    for (const n of roleHints) {
      const v = (n.getAttribute('data-testid') || '').toLowerCase();
      if (v.includes('user')) return 'user';
      if (v.includes('assistant')) return 'assistant';
    }

    // 휴리스틱 #2: 첫 줄에 "You/사용자/나/me" 같은 라벨이 있는지 확인
    const firstLine = txt.split('\n')[0]?.trim() || ''; // 헤더 라벨이 첫 줄에 있는 경우 대응
    if (/^(you|사용자|나|me)\b/i.test(firstLine)) return 'user';

    // 휴리스틱 #3: 확실히 모르겠으면 unknown 반환
    return 'unknown';
  }

  /**
   * 현재 스레드 메시지 목록 추출 메소드
   * - `<article>` 요소 기반 메시지 수집 기능
   * - 역할/텍스트 묶음 배열 반환 기능
   *
   * 반환 형식:
   * [
   *   { role: 'user'|'assistant'|'unknown', text: '...' },
   *   ...
   * ]
   */
  function extractMessages() {
    const articles = Array.from(document.querySelectorAll('article')); // 현재 스레드의 메시지 컨테이너들
    const msgs = []; // { role, text } 배열
    for (const a of articles) {
      const text = extractArticleText(a);
      if (!text) continue;                 // 빈 메시지는 무시
      msgs.push({ role: detectRole(a), text });
    }
    return msgs;
  }

  /**
   * 최근 Q/A 추출 메소드
   *
   * 현재 전략:
   * - 마지막 메시지 answer 가정 기능
   * - 직전 메시지 question 가정 기능
   *
   * 장점:
   * - DOM/역할 판별 불완전 환경 동작 기능
   *
   * 한계:
   * - 연속 사용자 메시지/연속 모델 답변 상황 정확도 저하 가능성
   */
  function extractRecentQA() {
    const msgs = extractMessages(); // 화면에서 읽은 전체 메시지 스냅샷
    if (msgs.length === 0) return null;

    // 마지막 메시지를 답변으로 가정
    const answerIdx = msgs.length - 1;      // 끝에서 1개: 최신 답변 후보
    const answer = msgs[answerIdx]?.text || '';

    // 바로 이전 메시지를 질문으로 가정
    const questionIdx = Math.max(0, answerIdx - 1); // 끝에서 2개: 직전 질문 후보
    const question = msgs[questionIdx]?.text || '';

    // 제목 후보: 질문의 첫 줄이 있으면 그걸, 없으면 답변 첫 줄을 사용
    const titleCandidate = (question.split('\n')[0] || answer.split('\n')[0] || 'ChatGPT Q&A')
      .replace(/\s+/g, ' ')               // 공백 정리
      .slice(0, 80);                      // 너무 길면 자르기(서버에서 최종 제목 생성)

    return {
      titleCandidate,
      question,
      answer,
      capturedAt: new Date().toISOString(), // 저장 요청 시각
      sourceUrl: location.href              // 현재 대화 URL
    };
  }

  /**
   * 선택 텍스트 추출 메소드
   * - 브라우저 선택 영역 문자열 추출 기능
   * - 정규화 후 반환 기능
   */
  function getSelectedText() {
    const sel = window.getSelection?.(); // 브라우저 선택 영역 API
    const t = sel ? sel.toString() : ''; // Range를 문자열로 평탄화
    return normalizeText(t);
  }

  /**
   * 저장된 databaseId 반환 메소드
   * - localStorage 우선 조회 기능
   * - 값 없을 때 DEFAULT_DATABASE_ID fallback 기능
   *
   * @returns {string} 저장된 databaseId 반환
   */
  function getStoredDatabaseId() {
    return (localStorage.getItem(DB_STORAGE_KEY) || DEFAULT_DATABASE_ID || '').trim();
  }

  /**
   * databaseId 저장 메소드
   * - 빈 문자열 저장 방지 기능
   * - localStorage 마지막 선택값 보관 기능
   *
   * @param {string} id 저장 대상 databaseId
   * @returns {void}
   */
  function setStoredDatabaseId(id) {
    if (!id) return;
    localStorage.setItem(DB_STORAGE_KEY, id);
  }

  /**
   * DB 메타 렌더링 메소드
   * - 오류 문구 우선 렌더링 기능
   * - 빈 상태 안내 문구 렌더링 기능
   * - 속성명/타입 목록 렌더링 기능
   *
   * @param {object|null} meta 서버 조회 DB 메타 정보
   * @param {string} [errorText] 메타 대신 표시할 오류 메시지
   * @returns {void}
   */
  function renderDbMeta(meta, errorText) {
    const box = getUiEl(UI_IDS.dbMeta); // 속성 목록 렌더링 대상 컨테이너
    const span = document.createElement('span');              // 안내 문구/빈 상태 표시용 노드
    if (!box) return;
    box.innerHTML = ''; // 이전 렌더링 결과 제거(재조회 시 중복 방지)

    if (errorText) {
      box.textContent = errorText;
      return;
    }

    const props = meta?.properties; // Notion DB property map
    if (!props || typeof props !== 'object' || Object.keys(props).length === 0) {
      span.className = 'tm-meta-empty';
      span.textContent = '데이터베이스 속성을 불러오세요.';
      box.appendChild(span);
      return;
    }

    const ul = document.createElement('ul'); // key-value를 사람이 스캔하기 쉬운 목록으로 표시
    for (const [name, value] of Object.entries(props)) {
      const li = document.createElement('li');
      const type = value?.type || '?'; // 서버 응답 불완전 시에도 UI가 깨지지 않게 fallback
      li.textContent = `${name} (${type})`;
      ul.appendChild(li);
    }
    box.appendChild(ul);
  }

  /**
   * DB 메타 조회 후 렌더링 메소드
   * - 로딩 문구 표시 기능
   * - 성공 시 속성 목록 렌더링 기능
   * - 실패 시 오류 문구 렌더링 기능
   *
   * @param {string} databaseId 조회 대상 데이터베이스 ID
   * @returns {Promise<object|null>} 성공 메타 객체 반환
   */
  async function fetchAndRenderDatabaseMeta(databaseId) {
    if (!databaseId) {
      renderDbMeta(null);
      return null;
    }

    renderDbMeta(null, '속성 조회 중...');
    const result = await fetchDatabaseMeta(databaseId);
    if (!result) {
      renderDbMeta(null);
      return null;
    }
    if (result.error) {
      renderDbMeta(null, result.error);
      return null;
    }

    renderDbMeta(result.data);
    return result.data;
  }

  /**
   * fallback 본문 생성 메소드
   * - DB 속성 힌트 앞단 삽입 기능
   * - Q/A 본문 조합 기능
   *
   * @param {{question: string, answer: string}} qa 최근 질문/답변 데이터
   * @param {string} propPrompt DB 속성 요약 프롬프트
   * @returns {string} 최종 저장 본문 반환
   */
  function buildFallbackContent(qa, propPrompt) {
    return propPrompt
      ? `# Notion properties\n${propPrompt}\n\n---\nQ:\n${qa.question}\n\nA:\n${qa.answer}`
      : `Q:\n${qa.question}\n\nA:\n${qa.answer}`;
  }

  /**
   * 최종 저장 payload 생성 메소드
   * - [notion] 블록 우선 파싱 기능
   * - fallback Q/A payload 조립 기능
   *
   * @param {object} params payload 생성 입력값 묶음
   * @param {string} params.dbId 저장 대상 DB ID
   * @param {object} params.qa 최근 질문/답변 정보
   * @param {string} params.selectedText 사용자가 드래그한 텍스트
   * @param {object} params.dbProps DB 메타에서 읽은 속성 정의
   * @param {object} params.userProps 사용자가 직접 입력한 properties
   * @returns {object} 서버 저장 요청 payload 반환
   */
  function buildSavePayload({ dbId, qa, selectedText, dbProps, userProps }) {
    const propPrompt = buildPropertyPrompt(dbProps);
    const sourceText = selectedText || qa.answer;
    const notionBlock = parseNotionBlock(sourceText);

    if (notionBlock) {
      return {
        databaseId: dbId,
        title: notionBlock.title,
        content: notionBlock.content,
        sourceUrl: qa.sourceUrl,
        capturedAt: qa.capturedAt,
        properties: userProps
      };
    }

    return {
      databaseId: dbId,
      title: qa.titleCandidate || 'ChatGPT Q&A',
      content: buildFallbackContent(qa, propPrompt),
      sourceUrl: qa.sourceUrl,
      capturedAt: qa.capturedAt,
      properties: userProps
    };
  }

  /**
   * properties 입력 파싱 메소드
   * - 입력창 부재 또는 빈 문자열 시 빈 객체 반환 기능
   * - JSON 파싱 실패 시 null 반환 기능
   *
   * @returns {object|null} 파싱된 properties 객체 반환
   */
  function readPropInput() {
    const propInput = getUiEl('tm-notion-props-json'); // 사용자 수동 입력 JSON textarea/input
    if (!propInput) return {};
    const raw = propInput.value.trim(); // 양끝 공백 제거 후 JSON 파싱
    if (!raw) return {};
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') return parsed;
      return {};
    } catch (e) {
      console.error('Invalid properties JSON', e);
      return null;
    }
  }

  /**
   * DB 속성 프롬프트 생성 메소드
   * - 속성명/타입 요약 문자열 생성 기능
   * - MAX_PROP_HINT 개수 제한 기능
   *
   * @param {Record<string, {type?: string, required?: boolean}>} props DB 속성 정의
   * @returns {string} 줄바꿈 연결 속성 요약 문자열 반환
   */
  function buildPropertyPrompt(props) {
    if (!props || typeof props !== 'object') return '';
    const entries = Object.entries(props).slice(0, MAX_PROP_HINT); // 프롬프트 길이 상한 적용
    return entries.map(([name, value]) => {
      const type = value?.type || '?'; // 속성 타입 표기
      const required = value?.hasOwnProperty('required') ? Boolean(value.required) : false; // required 표기 여부
      return `- ${name} (${type}${required ? ', required' : ''})`;
    }).join('\n');
  }

  /**
   * 공통 API 요청 메소드
   * - 메서드/URL/JSON body/인증 헤더 통합 처리 기능
   * - `gmRequest` 위임 전 요청 형식 통일 기능
   *
   * @param {object} params 요청 옵션
   * @param {string} params.method HTTP 메서드
   * @param {string} params.url 요청 URL
   * @param {object} [params.data] JSON 전송 요청 본문
   * @param {boolean} [params.withCredentials=false] 쿠키 포함 여부
   * @returns {Promise<object>} Tampermonkey 응답 객체 반환
   */
  async function requestApi({ method, url, data, withCredentials = false }) {
    const headers = buildClientHeaders(
      data === undefined ? {} : { 'Content-Type': 'application/json' }
    );
    const response = await gmRequest({
      method,
      url,
      headers,
      data: data === undefined ? undefined : JSON.stringify(data),
      withCredentials
    });
    return response;
  }

  /**
   * DB 메타 조회 메소드
   * - 특정 데이터베이스 속성 스키마 조회 기능
   * - 성공 시 `{ data }`, 실패 시 `{ error }` 반환 기능
   *
   * @param {string} databaseId 조회 대상 DB ID
   * @returns {Promise<{data?: object, error?: string}|null>} 조회 결과 반환
   */
  async function fetchDatabaseMeta(databaseId) {
    if (!databaseId) return null;

    try {
      const response = await requestApi({
        method: 'GET',
        url: `${DB_INFO_ENDPOINT}/${encodeURIComponent(databaseId)}`
      });
      if (!isSuccessfulStatus(response.status)) {
        return { error: `속성 조회 실패 (HTTP ${response.status})` };
      }

      const data = tryParseJson(response.responseText, '{}');
      if (data === null) {
        return { error: '속성 파싱 실패' };
      }
      return { data };
    } catch (e) {
      if (e.message === 'Request timeout') return { error: '속성 조회 시간초과' };
      return { error: '속성 조회 오류' };
    }
  }

  /**
   * 현재 databaseId 결정 메소드
   * - 우선순위: 직접 입력값 -> 셀렉트 선택값 -> 빈 문자열
   *
   * @returns {string} 결정된 databaseId 반환
   */
  function resolveDatabaseId() {
    const inputEl = getUiEl(UI_IDS.dbInput);   // 수동 입력 필드
    const selectEl = getUiEl(UI_IDS.dbSelect); // 사전 등록 옵션 셀렉트
    const inputVal = (inputEl?.value || '').trim(); // 수동 입력값(우선순위 1)
    if (inputVal) return inputVal;
    const selectVal = (selectEl?.value || '').trim(); // 옵션 선택값(우선순위 2)
    if (selectVal) return selectVal;
    return '';
  }

  /**
   * DB 옵션 조회 메소드
   * - 서버 사용 가능 DB 목록 조회 기능
   * - 실패 시 빈 배열 및 토스트 안내 기능
   *
   * @returns {Promise<Array<{id?: string, label?: string}>>} DB 옵션 배열 반환
   */
  async function fetchDatabaseOptions() {
    try {
      const response = await requestApi({
        method: 'GET',
        url: DB_LIST_ENDPOINT
      });
      if (!isSuccessfulStatus(response.status)) {
        const msg = `DB 목록 조회 실패 (HTTP ${response.status})`;
        console.warn(msg, response.responseText);
        toast(msg);
        return [];
      }

      const data = tryParseJson(response.responseText, '[]');
      if (data === null) {
        console.warn('DB 목록 파싱 실패');
        toast('DB 목록 파싱 실패');
        return [];
      }
      return Array.isArray(data) ? data : [];
    } catch (e) {
      const msg = e.message === 'Request timeout'
        ? 'DB 목록 조회 시간초과'
        : 'DB 목록 조회 오류';
      console.warn(msg);
      toast(msg);
      return [];
    }
  }

  /**
   * DB 옵션 캐시 반환 메소드
   * - 캐시 우선 반환 기능
   * - 캐시 부재 시 서버 조회 후 저장 기능
   * - 서버 결과 비어 있을 때 로컬 상수 fallback 기능
   *
   * @returns {Promise<Array<{id?: string, label?: string}>>} DB 옵션 배열 반환
   */
  async function getDatabaseOptions() {
    if (state.cachedDatabaseOptions.length > 0) return state.cachedDatabaseOptions;
    const fetched = await fetchDatabaseOptions(); // 최초 1회만 네트워크 조회
    state.cachedDatabaseOptions = (fetched && fetched.length > 0) ? fetched : DATABASE_ID_OPTIONS;
    return state.cachedDatabaseOptions;
  }

  /**
   * 저장 selection UI 반영 메소드
   * - 저장된 databaseId 기준 select/input 채움 기능
   * - 옵션 존재 여부 기준 표시 위치 결정 기능
   *
   * @returns {void}
   */
  function updateStoredSelectionToUI() {
    const select = getUiEl(UI_IDS.dbSelect); // 옵션 선택 UI
    const input = getUiEl(UI_IDS.dbInput);   // 직접 입력 UI
    const stored = getStoredDatabaseId(); // 마지막 사용한 DB id
    if (!select || !input) return;

    if (!stored) {
      input.value = '';
      select.value = '';
      renderDbMeta(null);
      return;
    }

    // 저장값이 현재 옵션 목록에 존재하는지 확인해 select/input 중 어디를 채울지 결정
    const hasOption = Array.from(select.options).some((o) => o.value === stored);
    if (hasOption) {
      select.value = stored;
      input.value = '';
    } else {
      input.value = stored;
      select.value = '';
    }
  }

  /**
   * DB select 옵션 채우기 메소드
   * - 기존 option 초기화 기능
   * - 기본 안내 option 및 실제 option 재구성 기능
   *
   * @param {Array<{id?: string, label?: string}>} options 렌더링 대상 옵션 목록
   * @returns {void}
   */
  function populateDatabaseSelect(options) {
    const select = getUiEl(UI_IDS.dbSelect); // 옵션을 채울 대상 select
    if (!select) return;

    // clear existing
    while (select.firstChild) select.removeChild(select.firstChild); // 이전 목록 초기화

    const defaultOpt = document.createElement('option');
    defaultOpt.value = '';
    defaultOpt.textContent = '옵션에서 선택 (없으면 아래 입력)';
    select.appendChild(defaultOpt);

    for (const opt of options || []) {
      if (!opt) continue;
      const o = document.createElement('option'); // DB 1개당 option 1개 생성
      o.value = opt.id || '';
      o.textContent = opt.label || opt.id || '';
      select.appendChild(o);
    }
  }

  /**
   * DB 옵션 및 메타 새로고침 메소드
   * - 옵션 조회/렌더링 기능
   * - 저장된 selection UI 반영 기능
   * - 현재 DB 메타 조회 및 표시 기능
   *
   * @returns {Promise<void>}
   */
  async function refreshDbOptionsAndMeta() {
    const options = await getDatabaseOptions(); // 서버/캐시에서 최신 옵션 확보
    populateDatabaseSelect(options);
    updateStoredSelectionToUI();
    await fetchAndRenderDatabaseMeta(resolveDatabaseId());
  }

  /**
   * 저장 요청 전송 메소드
   * - 서버 POST 전송 기능
   * - 2xx JSON 응답 파싱 기능
   * - HTTP/네트워크/JSON 오류 예외 발생 기능
   *
   * @param {object} payload 저장 요청 데이터
   * @returns {Promise<object>} 서버 응답 객체 반환
   */
  async function sendToServer(payload) {
    const response = await requestApi({
      method: 'POST',
      url: SAVE_ENDPOINT,
      data: payload,
      withCredentials: true
    });
    if (!isSuccessfulStatus(response.status)) {
      const text = response.responseText || '';
      throw new Error(`HTTP ${response.status}${text ? ` - ${text}` : ''}`);
    }

    const responseHeaders = response.responseHeaders || '';
    if (/content-type:\s*application\/json/i.test(responseHeaders)) {
      const parsed = tryParseJson(response.responseText, '{}');
      if (parsed === null) {
        throw new Error('Invalid JSON response');
      }
      return parsed;
    }

    return { ok: true };
  }

  /**
   * 저장 버튼 클릭 처리 메소드
   * - 버튼 비활성화 기반 중복 클릭 방지 기능
   * - 선택 텍스트 또는 최근 Q/A 기반 payload 조립 기능
   * - 서버 전송 및 성공/실패 토스트 표시 기능
   */
  async function onClickSave(btn) {
    try {
      btn.disabled = true;

      const dbId = resolveDatabaseId(); // 저장 대상 DB
      if (!dbId) {
        toast('databaseId를 선택하거나 입력하세요.');
        return;
      }
      setStoredDatabaseId(dbId);

      // 선택한 텍스트가 있으면 그걸 우선 저장(핵심만 저장할 때 유용)
      const selected = getSelectedText(); // 사용자가 드래그한 텍스트(있으면 우선)

      // 최근 Q/A 추출
      const qa = extractRecentQA(); // 선택 텍스트가 없어도 fallback 저장 가능한 기본 데이터
      if (!qa) {
        toast('대화를 찾지 못했습니다. (화면에 메시지가 있어야 합니다)');
        return;
      }

      // DB 속성 프롬프트 힌트 포함 처리
      const metaBoxData = await fetchDatabaseMeta(dbId);        // DB 스키마(프롬프트 힌트용)
      const dbProps = metaBoxData?.data?.properties || {};      // 속성 요약 생성 원본
      const userProps = readPropInput();                        // 사용자가 직접 지정한 properties JSON
      if (userProps === null) {
        toast('속성 JSON을 확인하세요.');
        return;
      }
      const payload = buildSavePayload({
        dbId,
        qa,
        selectedText: selected,
        dbProps,
        userProps
      });

      if (!payload.databaseId) {
        toast('databaseId를 선택하거나 입력하세요.');
        return;
      }

      toast('저장 요청 전송 중...');
      const out = await sendToServer(payload); // 서버 저장 결과(예: notionUrl 포함 가능)

      // 서버가 notionUrl을 응답하면 "링크가 포함됨" 정도로 안내
      if (out && out.notionUrl) {
        toast('저장 완료!\nNotion 링크가 응답에 포함됨');
        // 원하면 자동으로 Notion 페이지를 열 수 있음
        // window.open(out.notionUrl, '_blank');
      } else {
        toast('저장 완료!');
      }
    } catch (e) {
      toast(`저장 실패: ${e.message}`);
    } finally {
      btn.disabled = false;
    }
  }

  /**
   * 공통 필드 생성 메소드
   * - 라벨/입력 요소 세로 배치 래퍼 생성 기능
   * - 패널 UI 조립용 중복 DOM 생성 축소 기능
   *
   * @param {string} labelText 필드 라벨 텍스트
   * @param {HTMLElement} inputEl 실제 입력 요소
   * @returns {HTMLDivElement} 완성된 필드 래퍼 반환
   */
  function createField(labelText, inputEl) {
    const field = document.createElement('div');
    field.className = 'tm-field';

    const label = document.createElement('label');
    label.textContent = labelText;

    field.appendChild(label);
    field.appendChild(inputEl);
    return field;
  }

  /**
   * 메타 박스 생성 메소드
   * - 초기 안내 문구 포함 메타 표시 박스 생성 기능
   *
   * @returns {HTMLDivElement} 메타 표시 박스 요소 반환
   */
  function createMetaBox() {
    const metaBox = document.createElement('div');
    metaBox.id = UI_IDS.dbMeta;

    const empty = document.createElement('span');
    empty.className = 'tm-meta-empty';
    empty.textContent = '데이터베이스 속성을 불러오세요.';
    metaBox.appendChild(empty);

    return metaBox;
  }

  /**
   * DB 입력 충돌 방지 이벤트 바인딩 메소드
   * - select 선택 시 input 비움 기능
   * - input 직접 입력 시 select 비움 기능
   *
   * @param {HTMLSelectElement} select 사전 등록 옵션 셀렉트
   * @param {HTMLInputElement} input 직접 입력 필드
   * @returns {void}
   */
  function bindDatabaseSelectionInputs(select, input) {
    // select를 고르면 input은 자동으로 비워 "충돌 상태(둘 다 채움)"를 방지
    select.addEventListener('change', () => {
      if (select.value) input.value = '';
    });
    // input을 타이핑하면 select 선택을 비워 우선순위를 명확히 유지
    input.addEventListener('input', () => {
      if (input.value.trim()) select.value = '';
    });
  }

  /**
   * DB 선택 변경 처리 메소드
   * - 현재 DB 저장 기능
   * - 메타 재조회 및 렌더링 기능
   * - 값 없을 때 메타 박스 초기화 기능
   *
   * @returns {Promise<void>}
   */
  async function onDatabaseSelectionChanged() {
    const id = resolveDatabaseId();
    if (!id) {
      renderDbMeta(null);
      return;
    }

    setStoredDatabaseId(id);
    await fetchAndRenderDatabaseMeta(id);
  }

  /**
   * UI 보장 메소드
   * - 버튼/패널/토스트 부재 시 생성 기능
   * - SPA 리렌더 환경 중복 생성 방지 기능
   */
  function ensureUI() {
    // 이미 UI가 있으면 중복 생성하지 않음(SPA 리렌더 대응)
    if (getUiEl(UI_IDS.saveBtn)) return;

    // 우측 하단 패널
    const panel = document.createElement('div'); // DB 선택/메타 조회용 패널 컨테이너
    panel.id = UI_IDS.panel;

    const title = document.createElement('h4'); // 패널 제목
    title.textContent = 'Notion DB';

    const select = document.createElement('select'); // 사전 등록 옵션 목록
    select.id = UI_IDS.dbSelect;

    const input = document.createElement('input'); // DB id 직접 입력창
    input.id = UI_IDS.dbInput;
    input.type = 'text';
    input.placeholder = 'databaseId';
    bindDatabaseSelectionInputs(select, input);

    panel.appendChild(title);
    panel.appendChild(createField('사전 등록 DB', select));
    panel.appendChild(createField('직접 입력', input));
    panel.appendChild(createMetaBox());

    // 저장 버튼 생성
    const btn = document.createElement('button'); // 실제 저장 트리거 버튼
    btn.id = UI_IDS.saveBtn;
    btn.type = 'button';
    btn.textContent = 'Save (Notion)';
    btn.addEventListener('click', () => onClickSave(btn));

    // 토스트 영역 생성
    const toastEl = document.createElement('div'); // 사용자 피드백(성공/오류/진행) 영역
    toastEl.id = UI_IDS.toast;

    // DOM에 추가
    document.body.appendChild(panel);
    document.body.appendChild(btn);
    document.body.appendChild(toastEl);

    // 이벤트: 데이터베이스 변경 시 속성 다시 로드
    select.addEventListener('change', onDatabaseSelectionChanged);
    input.addEventListener('blur', onDatabaseSelectionChanged);

    // 초기 데이터 로드 (중복 호출 제거)
    refreshDbOptionsAndMeta();
  }

  /**
   * 스크립트 시작 메소드
   * - 최초 UI 보장 기능
   * - MutationObserver 기반 UI 재생성 보장 기능
   *
   * @returns {void}
   */
  function start() {
    ensureUI();

    // DOM 변화 감시: 버튼/패널이 사라지는 경우 재삽입
    const obs = new MutationObserver(() => ensureUI()); // DOM 변경 시 UI 존재 보장
    obs.observe(document.documentElement, { childList: true, subtree: true });
  }

  // 스크립트 실행 시작
  start();
})();
