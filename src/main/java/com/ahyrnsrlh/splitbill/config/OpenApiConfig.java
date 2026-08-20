package com.ahyrnsrlh.splitbill.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI splitBillOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Split Bill API")
                        .description("Allo Bank Backend Developer Take-Home Test: Split Bill API")
                        .version("1.0.0"));
    }
}
