package org.chandler25.websocket.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 14:02
 * @version 1.0.0
 * @since 21
 */
@Service
public class UserSessionService {
    // 存储用户名和会话ID的映射
    private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();

    // 存储当前在线的用户名
    private final CopyOnWriteArraySet<String> onlineUsers = new CopyOnWriteArraySet<>();

    /**
     * 用户加入聊天室
     */
    public void userJoin(String username, String sessionId) {
        userSessionMap.put(sessionId, username);
        onlineUsers.add(username);
    }

    /**
     * 用户离开聊天室
     */
    public void userLeave(String sessionId) {
        String username = userSessionMap.remove(sessionId);
        if (username != null) {
            onlineUsers.remove(username);
        }
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineCount() {
        return onlineUsers.size();
    }

    /**
     * 获取所有在线用户名
     */
    public CopyOnWriteArraySet<String> getOnlineUsers() {
        return new CopyOnWriteArraySet<>(onlineUsers);
    }

    /**
     * 根据会话ID获取用户名
     */
    public String getUsernameBySessionId(String sessionId) {
        return userSessionMap.get(sessionId);
    }

    /**
     * 检查用户名是否已存在
     */
    public boolean isUsernameExists(String username) {
        return onlineUsers.contains(username);
    }
}