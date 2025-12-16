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
package com.chandler.gateway.example.config;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Swagger3配置
 *
 * @author 钱丁君-chandler 4/3/21 10:25 AM
 * @version 1.0
 * @since 1.8
 */
@Configuration
public class Swagger3Config {

    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.OAS_30).select()
                // 指定对象为ApiOperation注解修饰的接口
//                .apis(RequestHandlerSelectors.withMethodAnnotation(Operation.class))
                .paths(PathSelectors.any())// 选any表示给这个controller包下所有的接口都生成文档
                .build().apiInfo(new ApiInfoBuilder().title("服务提供者工程swagger3页面")// 生成的接口文档的标题名称
                        .description("chandler微服务基础架构示例工程")// 文档摘要
                        .version("1.0.0")// API版本，可以自定义
                        .build());
    }
}
