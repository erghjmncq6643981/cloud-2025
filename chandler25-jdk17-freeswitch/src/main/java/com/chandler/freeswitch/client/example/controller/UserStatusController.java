package com.chandler.freeswitch.client.example.controller;

import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户状态管理控制器
 *
 * @author chandler
 * @since 1.0
 */
@RestController
@RequestMapping("user-status")
@Tag(name = "用户状态管理接口", description = "提供用户状态查询和管理功能")
public class UserStatusController {
    
    @Autowired
    private UserStatusService userStatusService;
    
    @Operation(description = "获取所有用户状态")
    @GetMapping("/all")
    public ResponseEntity<List<UserStatus>> getAllUserStatus() {
        return ResponseEntity.ok(userStatusService.getAllUserStatus());
    }
    
    @Operation(description = "获取指定用户状态")
    @GetMapping("/{userId}")
    public ResponseEntity<UserStatus> getUserStatus(@PathVariable String userId) {
        UserStatus userStatus = userStatusService.getUserStatus(userId);
        if (userStatus != null) {
            return ResponseEntity.ok(userStatus);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @Operation(description = "获取指定状态的用户列表")
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<UserStatus>> getUsersByStatus(@PathVariable String status) {
        return ResponseEntity.ok(userStatusService.getUsersByStatus(status));
    }
    
    @Operation(description = "获取用户统计信息")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userStatusService.getAllUserStatus().size());
        stats.put("onlineUsers", userStatusService.getOnlineUserCount());
        stats.put("idleUsers", userStatusService.getIdleUserCount());
        
        // 按状态分组统计
        Map<String, Long> statusCount = new HashMap<>();
        for (UserStatus userStatus : userStatusService.getAllUserStatus()) {
            statusCount.merge(userStatus.getStatus(), 1L, Long::sum);
        }
        stats.put("statusCount", statusCount);
        
        return ResponseEntity.ok(stats);
    }
    
    @Operation(description = "获取在线用户列表")
    @GetMapping("/online")
    public ResponseEntity<List<UserStatus>> getOnlineUsers() {
        return ResponseEntity.ok(userStatusService.getUsersByStatus("BRIDGE"));
    }
    
    @Operation(description = "获取空闲用户列表")
    @GetMapping("/idle")
    public ResponseEntity<List<UserStatus>> getIdleUsers() {
        return ResponseEntity.ok(userStatusService.getUsersByStatus("IDLE"));
    }
    
    @Operation(description = "获取通话中用户列表")
    @GetMapping("/in-call")
    public ResponseEntity<List<UserStatus>> getUsersInCall() {
        return ResponseEntity.ok(userStatusService.getUsersByStatus("ANSWERED"));
    }
    
    @Operation(description = "获取振铃中用户列表")
    @GetMapping("/ringing")
    public ResponseEntity<List<UserStatus>> getRingingUsers() {
        return ResponseEntity.ok(userStatusService.getUsersByStatus("RINGING"));
    }
    
}
