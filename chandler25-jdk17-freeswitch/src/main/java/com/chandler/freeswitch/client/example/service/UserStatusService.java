package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.domain.mapper.UserStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户状态服务
 *
 * @author chandler
 * @since 1.0
 */
@Slf4j
@Service
public class UserStatusService extends ServiceImpl<UserStatusMapper, UserStatus> {
    
    /**
     * 更新用户状态（存在则更新，不存在则插入）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(UserStatus userStatus) {
        UserStatus existing = getOne(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getUserId, userStatus.getUserId())
        );
        
        if (existing != null) {
            userStatus.setId(existing.getId());
            updateById(userStatus);
            log.debug("更新用户状态: {}", userStatus.getUserId());
        } else {
            save(userStatus);
            log.debug("插入用户状态: {}", userStatus.getUserId());
        }
    }
    
    /**
     * 获取用户状态
     */
    public UserStatus getUserStatus(String userId) {
        return getOne(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getUserId, userId)
        );
    }
    
    /**
     * 获取所有用户状态
     */
    public List<UserStatus> getAllUserStatus() {
        return list();
    }
    
    /**
     * 删除用户状态（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUserStatus(String userId) {
        remove(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getUserId, userId)
        );
    }
    
    /**
     * 清空所有用户状态（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearAllUserStatus() {
        remove(new LambdaQueryWrapper<>());
    }
    
    /**
     * 获取指定状态的用户列表
     */
    public List<UserStatus> getUsersByStatus(String status) {
        return list(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getStatus, status)
        );
    }
    
    /**
     * 获取在线用户数量
     */
    public long getOnlineUserCount() {
        return count(
            new LambdaQueryWrapper<UserStatus>()
                .ne(UserStatus::getStatus, "IDLE")
        );
    }
    
    /**
     * 获取空闲用户数量
     */
    public long getIdleUserCount() {
        return count(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getStatus, "IDLE")
        );
    }
    
    /**
     * 根据通话状态获取用户列表
     */
    public List<UserStatus> getUsersByCallStatus(String callStatus) {
        return list(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getCallStatus, callStatus)
        );
    }
    
    /**
     * 获取通话中的用户数量
     */
    public long getBusyUserCount() {
        return count(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getCallStatus, "BUSY")
        );
    }
    
    /**
     * 获取振铃中的用户数量
     */
    public long getRingingUserCount() {
        return count(
            new LambdaQueryWrapper<UserStatus>()
                .eq(UserStatus::getCallStatus, "RINGING")
        );
    }
}
