package com.gptToNotion.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.gptToNotion.client.NotionPagePayloadFactory;
import com.gptToNotion.dto.NotionPageCreateRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NotionPagePayloadFactoryTest {

    private final NotionPagePayloadFactory factory = new NotionPagePayloadFactory();

    @Test
    void buildSanitizesPropertiesAndAddsDefaultTitleProperty() {
        NotionPageCreateRequest request = new NotionPageCreateRequest(
                "페이지 제목",
                "본문",
                Map.of(
                        "상태", Map.of("select", Map.of("name", "Open")),
                        "태그", List.of("alpha", " beta ", ""),
                        "메모", "짧은 메모",
                        "무시", Map.of("id", "prop-1", "type", "select"),
                        "제목", Map.of("title", List.of(Map.of("text", Map.of("content", "다른 제목"))))
                )
        );

        NotionPagePayloadFactory.NotionCreatePagePayload payload = factory.build("db-1", request);
        Map<String, Object> properties = getProperties(payload.body());

        assertThat(properties).containsKeys("제목", "상태", "태그", "메모");
        assertThat(properties).doesNotContainKey("무시");
        assertThat(properties.get("상태")).isEqualTo(Map.of("select", Map.of("name", "Open")));
        assertThat(properties.get("태그")).isEqualTo(Map.of(
                "multi_select", List.of(
                        Map.of("name", "alpha"),
                        Map.of("name", "beta")
                )
        ));
        assertThat(properties.get("메모")).isEqualTo(Map.of(
                "rich_text", List.of(Map.of("text", Map.of("content", "짧은 메모")))
        ));
        assertThat(properties.get("제목")).isEqualTo(Map.of(
                "title", List.of(Map.of("text", Map.of("content", "페이지 제목")))
        ));
        assertThat(payload.remainingChildren()).isEmpty();
    }

    @Test
    void buildSplitsChildrenAfterFirstHundredBlocks() {
        String content = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "paragraph " + i)
                .collect(Collectors.joining("\n\n"));
        NotionPageCreateRequest request = new NotionPageCreateRequest("페이지 제목", content, null);

        NotionPagePayloadFactory.NotionCreatePagePayload payload = factory.build("db-1", request);

        assertThat(getChildren(payload.body())).hasSize(100);
        assertThat(payload.remainingChildren()).hasSize(1);
        assertThat(extractParagraphText(payload.remainingChildren().get(0))).isEqualTo("paragraph 101");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getProperties(Map<String, Object> body) {
        return (Map<String, Object>) body.get("properties");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getChildren(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("children");
    }

    @SuppressWarnings("unchecked")
    private String extractParagraphText(Map<String, Object> block) {
        Map<String, Object> paragraph = (Map<String, Object>) block.get("paragraph");
        List<Map<String, Object>> richText = (List<Map<String, Object>>) paragraph.get("rich_text");
        Map<String, Object> firstText = (Map<String, Object>) richText.get(0).get("text");
        return firstText.get("content").toString();
    }
}
