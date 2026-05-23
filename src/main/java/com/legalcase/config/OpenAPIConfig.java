package com.legalcase.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Legal Case Management Platform API")
                        .version("1.0.0")
                        .description("""
                                # AI-Assisted Legal Case Management and Collaboration Platform
                                
                                This API provides comprehensive legal case management functionality including:
                                
                                - **User Management**: Registration, authentication, role-based access (ADMIN/LAWYER/STAFF)
                                - **Case Management**: Create, update, track cases with full lifecycle (OPEN → IN_PROGRESS → CLOSED → ARCHIVED)
                                - **Task Management**: Task types (MANDATORY/OPTIONAL/REVIEW), workflow, dependencies, approvals
                                - **Document Management**: Upload PDF/DOCX/TXT/XLSX to S3, text extraction
                                - **AI Assistant**: Gemini-powered document analysis, Q&A, summarization
                                - **Collaboration**: Comments, mentions, real-time chat via WebSocket
                                - **Notifications**: In-app notifications for assignments, mentions, deadlines
                                """)
                        .contact(new Contact()
                                .name("LegalCase Support")
                                .email("support@legalcase.com")
                                .url("https://legalcase.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://legalcase.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080" + contextPath)
                                .description("Development Server"),
                        new Server()
                                .url("https://api.legalcase.com/api")
                                .description("Production Server")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token. Example: `eyJhbGciOiJIUzI1NiIs...`")));
    }
}