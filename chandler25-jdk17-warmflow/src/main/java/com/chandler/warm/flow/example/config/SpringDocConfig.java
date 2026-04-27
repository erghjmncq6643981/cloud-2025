package com.chandler.warm.flow.example.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author 钱丁君-chandler 2025/12/16
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info().title("Warm-Flow 学习 Demo")
                        .description("一个基于请假审批场景的 Warm-Flow 最小学习示例")
                        .version("v1")
                        .contact(new Contact().name("chandler"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                        .summary("示例接口"))
                .components(new Components());
    }
}
