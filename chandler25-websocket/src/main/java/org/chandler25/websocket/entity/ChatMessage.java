package org.chandler25.websocket.entity;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 13:25
 * @version 1.0.0
 * @since 21
 */
public class ChatMessage {
    public enum MessageType {
        CHAT, JOIN, LEAVE, TYPING
    }

    private MessageType type;
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    private Integer onlineCount;

    // 构造方法
    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatMessage(MessageType type, String sender, String content) {
        this();
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

    // Getter 和 Setter
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Integer getOnlineCount() { return onlineCount; }
    public void setOnlineCount(Integer onlineCount) { this.onlineCount = onlineCount; }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "type=" + type +
                ", sender='" + sender + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", onlineCount=" + onlineCount +
                '}';
    }
}