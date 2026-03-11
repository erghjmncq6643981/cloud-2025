package org.chandler25.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 13:26
 * @version 1.0.0
 * @since 21
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig  implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单内存消息代理，处理以"/topic"和"/queue"开头的消息
        config.enableSimpleBroker("/topic", "/queue");

        // 设置应用程序目的地前缀，以"/app"开头的消息会被路由到@MessageMapping方法
        config.setApplicationDestinationPrefixes("/app");

        // 设置用户目的地前缀（用于点对点消息）
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，客户端将连接到此端点
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // 启用SockJS回退选项
    }
}