package org.chandler25.websocket.entity;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 13:26
 * @version 1.0.0
 * @since 21
 */
public class UserJoinMessage {
    private String username;
    private MessageType type; // JOIN 或 LEAVE
    private LocalDateTime timestamp;
    private Integer onlineCount;

    public enum MessageType {
        JOIN, LEAVE
    }

    // 构造方法
    public UserJoinMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public UserJoinMessage(String username, MessageType type, Integer onlineCount) {
        this();
        this.username = username;
        this.type = type;
        this.onlineCount = onlineCount;
    }

    // Getter 和 Setter
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Integer getOnlineCount() { return onlineCount; }
    public void setOnlineCount(Integer onlineCount) { this.onlineCount = onlineCount; }
}