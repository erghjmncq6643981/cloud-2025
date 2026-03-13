package com.chandler.freeswitch.client.example.controller;

import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.GatewayStatusService;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 事件测试控制器
 *
 * @author chandler
 * @since 1.0
 */
@RestController
@RequestMapping("event-test")
public class EventTestController {
    
    @Autowired
    private UserStatusService userStatusService;
    
    @Autowired
    private GatewayStatusService gatewayStatusService;
    
    /**
     * 模拟用户状态事件
     */
    @PostMapping("/user-event")
    public ResponseEntity<Map<String, Object>> simulateUserEvent(
            @RequestParam String userId,
            @RequestParam String eventType,
            @RequestParam(required = false) String destinationNumber) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            UserStatus userStatus = UserStatus.builder()
                .userId(userId)
                .status(eventType.toUpperCase())
                .statusDescription(getStatusDescription(eventType))
                .channelUuid("test-channel-" + System.currentTimeMillis())
                .destinationNumber(destinationNumber != null ? destinationNumber : "2000")
                .callerNumber(userId)
                .updateTime(LocalDateTime.now())
                .build();
            
            userStatusService.updateUserStatus(userStatus);
            
            result.put("success", true);
            result.put("message", "User event processed successfully");
            result.put("userId", userId);
            result.put("eventType", eventType);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 模拟网关状态事件
     */
    @PostMapping("/gateway-event")
    public ResponseEntity<Map<String, Object>> simulateGatewayEvent(
            @RequestParam String gatewayName,
            @RequestParam String state) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            GatewayStatus.Status gatewayStatusEnum = mapGatewayState(state);
            
            GatewayStatus gatewayStatus = GatewayStatus.builder()
                .gatewayName(gatewayName)
                .status(gatewayStatusEnum.name())
                .statusDescription(gatewayStatusEnum.getDescription())
                .currentSessions((int) (Math.random() * 10))
                .maxSessions(100)
                .tryAttempts(100)
                .successAttempts(gatewayStatusEnum == GatewayStatus.Status.REGISTED ? 95 : 0)
                .failAttempts(gatewayStatusEnum == GatewayStatus.Status.REGISTED ? 5 : 100)
                .lastRegisterTime(LocalDateTime.now().minusMinutes((long) (Math.random() * 60)))
                .updateTime(LocalDateTime.now())
                .build();
            
            gatewayStatusService.updateGatewayStatus(gatewayStatus);
            
            result.put("success", true);
            result.put("message", "Gateway event processed successfully");
            result.put("gatewayName", gatewayName);
            result.put("state", state);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取状态描述
     */
    private String getStatusDescription(String eventType) {
        switch (eventType.toUpperCase()) {
            case "RINGING":
                return "振铃";
            case "ANSWERED":
                return "已接听";
            case "BRIDGE":
                return "通话中";
            case "HANGUP":
                return "已挂断";
            case "IDLE":
                return "空闲";
            default:
                return "未知状态";
        }
    }
    
    /**
     * 映射网关状态
     */
    private GatewayStatus.Status mapGatewayState(String state) {
        switch (state.toUpperCase()) {
            case "REGISTED":
                return GatewayStatus.Status.REGISTED;
            case "UNREGISTED":
                return GatewayStatus.Status.UNREGISTED;
            case "REGISTERING":
                return GatewayStatus.Status.REGISTERING;
            case "UNREGISTERING":
                return GatewayStatus.Status.UNREGISTERING;
            case "FAILED":
                return GatewayStatus.Status.FAILED;
            case "NO_RESPONSE":
                return GatewayStatus.Status.NO_RESPONSE;
            case "TIMEOUT":
                return GatewayStatus.Status.TIMEOUT;
            default:
                return GatewayStatus.Status.UNKNOWN;
        }
    }
}
