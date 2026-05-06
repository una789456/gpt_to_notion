package com.gptToNotion;

import com.gptToNotion.config.NotionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = NotionProperties.class)
public class GptToNotionApplication {

    /**
     * Spring Boot 애플리케이션 진입 메소드
     * - 자동설정, 컴포넌트 스캔, 설정 바인딩 초기화 기능
     */
    public static void main(String[] args) {
        SpringApplication.run(GptToNotionApplication.class, args);
    }
}
