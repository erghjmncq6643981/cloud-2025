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
}
