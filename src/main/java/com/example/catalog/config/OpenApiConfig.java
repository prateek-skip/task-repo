package com.example.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI catalogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Product Catalog API")
                .version("v1")
                .description("Create, read, update and delete products, and list them by category and "
                        + "price range with cursor pagination. Errors are RFC 9457 problem details."));
    }
}
