package com.smartcane.transit.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.ExternalDocumentation;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "transit Service API",
                version = "v1",
                description = "대중교통API 문서",
                contact = @Contact(name = "SmartCane", email = "support@smartcane.local")
        )
)
// (선택) JWT 보안 스키마 사전 정의: 실제 Security 붙이면 사용
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        in = SecuritySchemeIn.HEADER
)
@Configuration
public class OpenApiConfig {

    /**
     * 전역 커스터마이저 예시:
     * - 스키마/태그/외부 문서 등 추가 커스터마이즈 가능
     */
    @Bean
    public OpenApiCustomizer globalOpenApiCustomizer() {
        return openApi -> openApi
                .externalDocs(new ExternalDocumentation()
                        .description("SmartCane Docs")
                        .url("https://example.com/docs"));
    }
}
