package com.chandler.freeswitch.client.example.service;

import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关状态服务
 *
 * @author chandler
 * @since 1.0
 */
@Service
public class GatewayStatusService {
    
    private final Map<String, GatewayStatus> gatewayStatusMap = new ConcurrentHashMap<>();
    
    /**
     * 更新网关状态
     */
    public void updateGatewayStatus(GatewayStatus gatewayStatus) {
        gatewayStatusMap.put(gatewayStatus.getGatewayName(), gatewayStatus);
    }
    
    /**
     * 获取网关状态
     */
    public GatewayStatus getGatewayStatus(String gatewayName) {
        return gatewayStatusMap.get(gatewayName);
    }
    
    /**
     * 获取所有网关状态
     */
    public List<GatewayStatus> getAllGatewayStatus() {
        return List.copyOf(gatewayStatusMap.values());
    }
    
    /**
     * 删除网关状态
     */
    public void removeGatewayStatus(String gatewayName) {
        gatewayStatusMap.remove(gatewayName);
    }
    
    /**
     * 清空所有网关状态
     */
    public void clearAllGatewayStatus() {
        gatewayStatusMap.clear();
    }
    
    /**
     * 获取指定状态的网关列表
     */
    public List<GatewayStatus> getGatewaysByStatus(String status) {
        return gatewayStatusMap.values().stream()
            .filter(gatewayStatus -> status.equals(gatewayStatus.getStatus()))
            .toList();
    }
    
    /**
     * 获取在线网关数量
     */
    public long getOnlineGatewayCount() {
        return gatewayStatusMap.values().stream()
            .filter(gatewayStatus -> GatewayStatus.Status.REGISTED.name().equals(gatewayStatus.getStatus()))
            .count();
    }
    
    /**
     * 获取离线网关数量
     */
    public long getOfflineGatewayCount() {
        return gatewayStatusMap.values().stream()
            .filter(gatewayStatus -> !GatewayStatus.Status.REGISTED.name().equals(gatewayStatus.getStatus()))
            .count();
    }
    
    /**
     * 获取总会话数
     */
    public int getTotalSessions() {
        return gatewayStatusMap.values().stream()
            .mapToInt(gateway -> gateway.getCurrentSessions() != null ? gateway.getCurrentSessions() : 0)
            .sum();
    }
    
    /**
     * 获取最大会话数
     */
    public int getMaxSessions() {
        return gatewayStatusMap.values().stream()
            .mapToInt(gateway -> gateway.getMaxSessions() != null ? gateway.getMaxSessions() : 0)
            .sum();
    }
}
