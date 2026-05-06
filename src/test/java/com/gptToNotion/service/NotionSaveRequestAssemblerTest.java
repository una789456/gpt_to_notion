package com.gptToNotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gptToNotion.dto.NotionPageCreateRequest;
import com.gptToNotion.dto.NotionSaveRequest;
import java.util.Map;

import com.gptToNotion.service.NotionSaveRequestAssembler;
import org.junit.jupiter.api.Test;

class NotionSaveRequestAssemblerTest {

    private final NotionSaveRequestAssembler assembler = new NotionSaveRequestAssembler();

    @Test
    void assembleRemovesEmbeddedPropertiesAndNormalizesSeparatedBullets() {
        NotionSaveRequest request = new NotionSaveRequest(
                "db-1",
                "  명시 제목  ",
                """
                properties: {
                  "상태": { "select": { "name": "Open" } }
                }

                -
                다음 작업
                """,
                null,
                null,
                null,
                null,
                null,
                Map.of(
                        "상태", Map.of("select", Map.of("name", "Closed")),
                        "메모", "keep me"
                )
        );

        NotionPageCreateRequest pageRequest = assembler.assemble(request);

        assertThat(pageRequest.title()).isEqualTo("명시 제목");
        assertThat(pageRequest.content()).isEqualTo("- 다음 작업");
        assertThat(pageRequest.properties()).containsKeys("상태", "메모", "제목");
        assertThat(pageRequest.properties().get("상태"))
                .isEqualTo(Map.of("select", Map.of("name", "Open")));
        assertThat(pageRequest.properties().get("메모")).isEqualTo("keep me");
    }

    @Test
    void assembleBuildsFallbackTitleAndContentFromQuestionAndAnswer() {
        NotionSaveRequest request = new NotionSaveRequest(
                "db-1",
                null,
                null,
                null,
                "질문 제목\n상세 질문",
                "답변 내용",
                "https://example.com/chat/1",
                "2026-04-02T10:15:30Z",
                null
        );

        NotionPageCreateRequest pageRequest = assembler.assemble(request);

        assertThat(pageRequest.title()).isEqualTo("질문 제목");
        assertThat(pageRequest.content()).isEqualTo("""
                Q:
                질문 제목
                상세 질문

                A:
                답변 내용

                Source: https://example.com/chat/1
                CapturedAt: 2026-04-02T10:15:30Z""");
        assertThat(pageRequest.properties()).containsKey("제목");
    }

    @Test
    void assembleRejectsBlankContentAfterRemovingEmbeddedProperties() {
        NotionSaveRequest request = new NotionSaveRequest(
                "db-1",
                null,
                """
                properties: {
                  "상태": { "select": { "name": "Open" } }
                }
                """,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> assembler.assemble(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content or answer is required");
    }
}
