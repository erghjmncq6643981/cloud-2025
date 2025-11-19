package org.chandler25.ai.demo;

import org.chandler25.ai.demo.config.properties.AuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 类功能描述
 *
 * @version 1.0.0  
 * @author 钱丁君-chandler 2025/7/18 15:35
 * @since 1.8
 */
@MapperScan("org.chandler25.ai.demo.respository")
@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
