package io.r3k.idempotency.evaluate.arun0009lib.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BASIC_AUTH = "basicAuth";

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Idempotency API")
                .version("0.0.1")
                .description(
                    "Idempotency evaluation API using arun0009 library. "
                        + "HTTP Basic Auth required; the username is the tenant marker."))
        .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH))
        .components(
            new Components()
                .addSecuritySchemes(
                    BASIC_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("Username is the tenant marker; password is not validated.")));
  }
}
