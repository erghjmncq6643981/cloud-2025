package com.chandler.key.example.config;

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
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 配置接口文档基本信息
                .info(this.getApiInfo());
    }

    private Info getApiInfo() {
        return new Info()
                // 配置文档标题
                .title("秘钥系统 Demo")
                // 配置文档描述
                .description("字段加解密验证")
                // 配置作者信息
                .contact(new Contact().name("chandler"))
                // 配置License许可证信息
                .license(new License().name("Apache 2.0"))
                // 概述信息
                .summary("秘钥系统的ORM层字段加解密验证")
                .termsOfService("https://www.xiezhrspace.cn")
                // 配置版本号
                .version("2.0");
    }
}
