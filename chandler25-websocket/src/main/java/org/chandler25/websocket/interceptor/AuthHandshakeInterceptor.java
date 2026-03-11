package org.chandler25.websocket.interceptor;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 13:53
 * @version 1.0.0
 * @since 21
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        // 从请求中提取认证信息（如JWT token）
        String token = extractTokenFromRequest(request);
        if (token != null && validateToken(token)) {
            String username = extractUsernameFromToken(token);
            attributes.put("username", username);
            return true;
        }

        // 认证失败，拒绝连接
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后的处理逻辑
    }

    private String extractTokenFromRequest(ServerHttpRequest request) {
        // 从请求头或参数中提取token
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        return null;
    }

    private boolean validateToken(String token) {
        // 实现token验证逻辑
        return true; // 简化示例
    }

    private String extractUsernameFromToken(String token) {
        // 从token中提取用户名
        return "user_" + System.currentTimeMillis(); // 简化示例
    }
}