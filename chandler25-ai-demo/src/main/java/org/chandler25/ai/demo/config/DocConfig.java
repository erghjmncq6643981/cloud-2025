/*
 * chandler-instance-client
 * 4/3/21 10:25 AM
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.ai.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger3配置
 *
 * @author 钱丁君-chandler 4/3/21 10:25 AM
 * @version 1.0
 * @since 1.8
 */
@Configuration
public class DocConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info().title("学习助手")
                        .description("访问AI模型接口，然后将结果转成为笔记") // API描述信息
                        .version("v1") // API版本号
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .components(new Components()
                        .addSecuritySchemes("Authorization",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .name("Authorization")
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Bearer Token 认证")))
                // 添加全局安全需求
                .addSecurityItem(new SecurityRequirement().addList("Authorization")); // API许可信息
    }
}
