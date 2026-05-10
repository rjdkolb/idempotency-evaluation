package io.r3k.idempotency.evaluate.arun0009lib.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Idempotency API")
                .version("0.0.1")
                .description("Idempotency evaluation API using arun0009 library"));
  }
}
