package com.chandler.freeswitch.client.example.service;

import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户状态服务
 *
 * @author chandler
 * @since 1.0
 */
@Service
public class UserStatusService {
    
    private final Map<String, UserStatus> userStatusMap = new ConcurrentHashMap<>();
    
    /**
     * 更新用户状态
     */
    public void updateUserStatus(UserStatus userStatus) {
        userStatusMap.put(userStatus.getUserId(), userStatus);
    }
    
    /**
     * 获取用户状态
     */
    public UserStatus getUserStatus(String userId) {
        return userStatusMap.get(userId);
    }
    
    /**
     * 获取所有用户状态
     */
    public List<UserStatus> getAllUserStatus() {
        return List.copyOf(userStatusMap.values());
    }
    
    /**
     * 删除用户状态
     */
    public void removeUserStatus(String userId) {
        userStatusMap.remove(userId);
    }
    
    /**
     * 清空所有用户状态
     */
    public void clearAllUserStatus() {
        userStatusMap.clear();
    }
    
    /**
     * 获取指定状态的用户列表
     */
    public List<UserStatus> getUsersByStatus(String status) {
        return userStatusMap.values().stream()
            .filter(userStatus -> status.equals(userStatus.getStatus()))
            .toList();
    }
    
    /**
     * 获取在线用户数量
     */
    public long getOnlineUserCount() {
        return userStatusMap.values().stream()
            .filter(userStatus -> !UserStatus.Status.IDLE.name().equals(userStatus.getStatus()))
            .count();
    }
    
    /**
     * 获取空闲用户数量
     */
    public long getIdleUserCount() {
        return userStatusMap.values().stream()
            .filter(userStatus -> UserStatus.Status.IDLE.name().equals(userStatus.getStatus()))
            .count();
    }
}
