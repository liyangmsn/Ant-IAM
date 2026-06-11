package com.antiam.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 文档的基础信息，便于前后端和外部系统识别接口用途。
     */
    @Bean
    OpenAPI antIamOpenApi() {
        return new OpenAPI()
            .components(new Components().addSecuritySchemes("basicAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")))
            .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
            .info(new Info()
                .title("Ant IAM 接口文档")
                .version("0.1.0")
                .description("基于 Spring Boot 4、PostgreSQL、JPA 和 Liquibase 构建的 IAM / IDaaS 后端接口。"));
    }
}
