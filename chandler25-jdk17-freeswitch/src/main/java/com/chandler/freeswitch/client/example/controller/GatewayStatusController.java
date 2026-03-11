package com.chandler.freeswitch.client.example.controller;

import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import com.chandler.freeswitch.client.example.service.GatewayStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关状态管理控制器
 *
 * @author chandler
 * @since 1.0
 */
@RestController
@RequestMapping("gateway-status")
@Tag(name = "网关状态管理接口", description = "提供网关状态查询和管理功能")
public class GatewayStatusController {
    
    @Autowired
    private GatewayStatusService gatewayStatusService;
    
    @Operation(description = "获取所有网关状态")
    @GetMapping("/all")
    public ResponseEntity<List<GatewayStatus>> getAllGatewayStatus() {
        return ResponseEntity.ok(gatewayStatusService.getAllGatewayStatus());
    }
    
    @Operation(description = "获取指定网关状态")
    @GetMapping("/{gatewayName}")
    public ResponseEntity<GatewayStatus> getGatewayStatus(@PathVariable String gatewayName) {
        GatewayStatus gatewayStatus = gatewayStatusService.getGatewayStatus(gatewayName);
        if (gatewayStatus != null) {
            return ResponseEntity.ok(gatewayStatus);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @Operation(description = "获取指定状态的网关列表")
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<GatewayStatus>> getGatewaysByStatus(@PathVariable String status) {
        return ResponseEntity.ok(gatewayStatusService.getGatewaysByStatus(status));
    }
    
    @Operation(description = "获取网关统计信息")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGatewayStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGateways", gatewayStatusService.getAllGatewayStatus().size());
        stats.put("onlineGateways", gatewayStatusService.getOnlineGatewayCount());
        stats.put("offlineGateways", gatewayStatusService.getOfflineGatewayCount());
        stats.put("totalSessions", gatewayStatusService.getTotalSessions());
        stats.put("maxSessions", gatewayStatusService.getMaxSessions());
        
        // 按状态分组统计
        Map<String, Long> statusCount = new HashMap<>();
        for (GatewayStatus gatewayStatus : gatewayStatusService.getAllGatewayStatus()) {
            statusCount.merge(gatewayStatus.getStatus(), 1L, Long::sum);
        }
        stats.put("statusCount", statusCount);
        
        // 计算使用率
        int totalSessions = gatewayStatusService.getTotalSessions();
        int maxSessions = gatewayStatusService.getMaxSessions();
        double utilizationRate = maxSessions > 0 ? (double) totalSessions / maxSessions * 100 : 0;
        stats.put("utilizationRate", String.format("%.2f%%", utilizationRate));
        
        return ResponseEntity.ok(stats);
    }
    
    @Operation(description = "获取在线网关列表")
    @GetMapping("/online")
    public ResponseEntity<List<GatewayStatus>> getOnlineGateways() {
        return ResponseEntity.ok(gatewayStatusService.getGatewaysByStatus("REGISTED"));
    }
    
    @Operation(description = "获取离线网关列表")
    @GetMapping("/offline")
    public ResponseEntity<List<GatewayStatus>> getOfflineGateways() {
        return ResponseEntity.ok(gatewayStatusService.getGatewaysByStatus("UNREGISTED"));
    }
    
    @Operation(description = "获取注册失败的网关列表")
    @GetMapping("/failed")
    public ResponseEntity<List<GatewayStatus>> getFailedGateways() {
        return ResponseEntity.ok(gatewayStatusService.getGatewaysByStatus("FAILED"));
    }
    
    @Operation(description = "获取会话使用率最高的网关")
    @GetMapping("/top-usage")
    public ResponseEntity<List<GatewayStatus>> getTopUsageGateways() {
        return ResponseEntity.ok(gatewayStatusService.getAllGatewayStatus().stream()
            .sorted((a, b) -> {
                int sessionsA = a.getCurrentSessions() != null ? a.getCurrentSessions() : 0;
                int sessionsB = b.getCurrentSessions() != null ? b.getCurrentSessions() : 0;
                return Integer.compare(sessionsB, sessionsA);
            })
            .limit(5)
            .toList());
    }
    
    @Operation(description = "清空所有网关状态")
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearAllGatewayStatus() {
        gatewayStatusService.clearAllGatewayStatus();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "All gateway status data cleared successfully");
        return ResponseEntity.ok(response);
    }
    
    @Operation(description = "删除指定网关状态")
    @DeleteMapping("/{gatewayName}")
    public ResponseEntity<Map<String, String>> removeGatewayStatus(@PathVariable String gatewayName) {
        gatewayStatusService.removeGatewayStatus(gatewayName);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Gateway status for " + gatewayName + " removed successfully");
        return ResponseEntity.ok(response);
    }
    
    @Operation(description = "获取网关会话分布")
    @GetMapping("/session-distribution")
    public ResponseEntity<Map<String, Object>> getSessionDistribution() {
        Map<String, Object> distribution = new HashMap<>();
        
        List<GatewayStatus> gateways = gatewayStatusService.getAllGatewayStatus();
        
        // 按会话数分组
        Map<String, Integer> sessionRanges = new HashMap<>();
        sessionRanges.put("0-10", 0);
        sessionRanges.put("11-50", 0);
        sessionRanges.put("51-100", 0);
        sessionRanges.put("100+", 0);
        
        for (GatewayStatus gateway : gateways) {
            int sessions = gateway.getCurrentSessions() != null ? gateway.getCurrentSessions() : 0;
            if (sessions <= 10) {
                sessionRanges.put("0-10", sessionRanges.get("0-10") + 1);
            } else if (sessions <= 50) {
                sessionRanges.put("11-50", sessionRanges.get("11-50") + 1);
            } else if (sessions <= 100) {
                sessionRanges.put("51-100", sessionRanges.get("51-100") + 1);
            } else {
                sessionRanges.put("100+", sessionRanges.get("100+") + 1);
            }
        }
        
        distribution.put("sessionRanges", sessionRanges);
        distribution.put("totalGateways", gateways.size());
        
        return ResponseEntity.ok(distribution);
    }
}
