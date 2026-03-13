package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import com.chandler.freeswitch.client.example.domain.mapper.GatewayStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 网关状态服务
 *
 * @author chandler
 * @since 1.0
 */
@Slf4j
@Service
public class GatewayStatusService extends ServiceImpl<GatewayStatusMapper, GatewayStatus> {
    
    /**
     * 更新网关状态（存在则更新，不存在则插入）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGatewayStatus(GatewayStatus gatewayStatus) {
        GatewayStatus existing = getOne(
            new LambdaQueryWrapper<GatewayStatus>()
                .eq(GatewayStatus::getGatewayName, gatewayStatus.getGatewayName())
        );
        
        if (existing != null) {
            gatewayStatus.setId(existing.getId());
            updateById(gatewayStatus);
            log.debug("更新网关状态: {}", gatewayStatus.getGatewayName());
        } else {
            save(gatewayStatus);
            log.debug("插入网关状态: {}", gatewayStatus.getGatewayName());
        }
    }
    
    /**
     * 获取网关状态
     */
    public GatewayStatus getGatewayStatus(String gatewayName) {
        return getOne(
            new LambdaQueryWrapper<GatewayStatus>()
                .eq(GatewayStatus::getGatewayName, gatewayName)
        );
    }
    
    /**
     * 获取所有网关状态
     */
    public List<GatewayStatus> getAllGatewayStatus() {
        return list();
    }
    
    /**
     * 删除网关状态（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeGatewayStatus(String gatewayName) {
        remove(
            new LambdaQueryWrapper<GatewayStatus>()
                .eq(GatewayStatus::getGatewayName, gatewayName)
        );
    }
    
    /**
     * 清空所有网关状态（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearAllGatewayStatus() {
        remove(new LambdaQueryWrapper<>());
    }
    
    /**
     * 获取指定状态的网关列表
     */
    public List<GatewayStatus> getGatewaysByStatus(String status) {
        return list(
            new LambdaQueryWrapper<GatewayStatus>()
                .eq(GatewayStatus::getState, status)
        );
    }
    
    /**
     * 获取在线网关数量
     */
    public long getOnlineGatewayCount() {
        return count(
            new LambdaQueryWrapper<GatewayStatus>()
                .eq(GatewayStatus::getState, "REGED")
        );
    }
    
    /**
     * 获取离线网关数量
     */
    public long getOfflineGatewayCount() {
        return count(
            new LambdaQueryWrapper<GatewayStatus>()
                .ne(GatewayStatus::getState, "REGED")
        );
    }
}
