package com.fileindex.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
            .title("File Search API")
            .description("Индексация файлов на диске и полнотекстовый поиск (Spring Boot + Elasticsearch).")
            .version("v1")
        );
    }

    // /api/auth/login and /api/auth/logout are handled by Spring Security's form-login/logout
    // filters, not @Controller methods, so springdoc's classpath scan never finds them. Adding
    // them here manually is the only way they show up in the generated spec at all.
    @Bean
    public OpenApiCustomizer authEndpointsCustomizer() {
        return openApi -> openApi.getPaths()
            .addPathItem("/api/auth/login", new PathItem().post(new Operation()
                .summary("Войти")
                .description("Обрабатывается Spring Security (form-urlencoded username/password), не обычный @Controller-метод.")
                .responses(new ApiResponses()
                    .addApiResponse("200", new ApiResponse().description("Успешный вход, установлена сессионная кука"))
                    .addApiResponse("401", new ApiResponse().description("Неверный логин или пароль")))
            ))
            .addPathItem("/api/auth/logout", new PathItem().post(new Operation()
                .summary("Выйти")
                .description("Обрабатывается Spring Security.")
                .responses(new ApiResponses()
                    .addApiResponse("200", new ApiResponse().description("Успешный выход")))
            ));
    }
}
