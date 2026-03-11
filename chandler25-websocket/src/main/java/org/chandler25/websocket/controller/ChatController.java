package org.chandler25.websocket.controller;

import org.chandler25.websocket.entity.ChatMessage;
import org.chandler25.websocket.service.UserSessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 14:15
 * @version 1.0.0
 * @since 21
 */
@Controller
public class ChatController {
    private final UserSessionService userSessionService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(UserSessionService userSessionService,
                          SimpMessagingTemplate messagingTemplate) {
        this.userSessionService = userSessionService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 处理公共聊天消息
     * 客户端发送到 /app/chat.send 的消息会路由到这里
     */
    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        // 设置时间戳
        message.setTimestamp(LocalDateTime.now());

        // 记录日志
        System.out.println("收到公共消息: " + message);

        return message;
    }

    /**
     * 处理用户加入聊天室
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String username = message.getSender();
        String sessionId = headerAccessor.getSessionId();

        // 检查用户名是否已存在
        if (userSessionService.isUsernameExists(username)) {
            // 发送错误消息到私人队列
            ChatMessage errorMsg = new ChatMessage();
            errorMsg.setType(ChatMessage.MessageType.CHAT);
            errorMsg.setSender("系统");
            errorMsg.setContent("用户名 " + username + " 已存在，请选择其他用户名");
            errorMsg.setTimestamp(LocalDateTime.now());

            messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            return null;
        }

        // 用户加入成功
        userSessionService.userJoin(username, sessionId);

        // 设置消息类型为加入
        message.setType(ChatMessage.MessageType.JOIN);
        message.setTimestamp(LocalDateTime.now());
        message.setOnlineCount(userSessionService.getOnlineCount());
        message.setContent(username + " 加入了聊天室");

        System.out.println("用户加入: " + username + ", 当前在线: " + userSessionService.getOnlineCount());

        // 广播在线用户列表更新
        broadcastOnlineUsers();

        return message;
    }

    /**
     * 处理用户离开聊天室
     * 注意：这个方法通常由断开连接事件触发
     */
    public void handleUserLeave(String sessionId) {
        String username = userSessionService.getUsernameBySessionId(sessionId);
        if (username != null) {
            userSessionService.userLeave(sessionId);

            ChatMessage leaveMessage = new ChatMessage();
            leaveMessage.setType(ChatMessage.MessageType.LEAVE);
            leaveMessage.setSender(username);
            leaveMessage.setContent(username + " 离开了聊天室");
            leaveMessage.setTimestamp(LocalDateTime.now());
            leaveMessage.setOnlineCount(userSessionService.getOnlineCount());

            // 广播用户离开消息
            messagingTemplate.convertAndSend("/topic/public", leaveMessage);

            // 广播在线用户列表更新
            broadcastOnlineUsers();

            System.out.println("用户离开: " + username + ", 剩余在线: " + userSessionService.getOnlineCount());
        }
    }

    /**
     * 处理私聊消息
     */
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(ChatMessage message) {
        message.setType(ChatMessage.MessageType.CHAT);
        message.setTimestamp(LocalDateTime.now());

        // 发送给特定用户
        messagingTemplate.convertAndSendToUser(
                message.getContent().split(":")[0], // 简单解析接收者
                "/queue/private",
                message
        );

        // 同时发送回发送者（可选）
        messagingTemplate.convertAndSendToUser(
                message.getSender(),
                "/queue/private",
                message
        );
    }

    /**
     * 处理输入状态消息
     */
    @MessageMapping("/chat.typing")
    @SendTo("/topic/typing")
    public ChatMessage userTyping(ChatMessage message) {
        message.setTimestamp(LocalDateTime.now());
        return message;
    }

    /**
     * 广播在线用户列表
     */
    private void broadcastOnlineUsers() {
        messagingTemplate.convertAndSend("/topic/online.users",
                userSessionService.getOnlineUsers());
    }

    /**
     * 处理WebSocket连接断开事件
     */
    @org.springframework.context.event.EventListener
    public void handleWebSocketDisconnectListener(
            org.springframework.web.socket.messaging.SessionDisconnectEvent event) {

        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        handleUserLeave(sessionId);
    }

    /**
     * 首页
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}