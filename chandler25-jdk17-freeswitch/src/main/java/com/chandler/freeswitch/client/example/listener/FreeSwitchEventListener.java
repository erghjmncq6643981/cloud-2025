package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.GatewayStatusService;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FreeSWITCH事件监听器
 *
 * @author chandler
 * @since 1.0
 */
@Slf4j
@Component
public class FreeSwitchEventListener {
    
    @Autowired
    private UserStatusService userStatusService;
    
    @Autowired
    private GatewayStatusService gatewayStatusService;
    
    @Value("${freeswitch.esl.host:localhost}")
    private String host;
    
    @Value("${freeswitch.esl.port:8021}")
    private int port;
    
    @Value("${freeswitch.esl.password:ClueCon}")
    private String password;
    
    private final ConcurrentHashMap<String, UserStatus> userStatusCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GatewayStatus> gatewayStatusCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    
    @PostConstruct
    public void init() {
        log.info("Initializing FreeSWITCH Event Listener");
        
        // 初始化定时任务，模拟状态更新
        startStatusSimulation();
        
        // 尝试连接到FreeSWITCH（这里使用模拟数据）
        tryConnectToFreeSwitch();
    }
    
    /**
     * 启动状态模拟
     */
    private void startStatusSimulation() {
        scheduler = Executors.newScheduledThreadPool(2);
        
        // 模拟用户状态更新
        scheduler.scheduleAtFixedRate(this::simulateUserStatusUpdate, 10, 30, TimeUnit.SECONDS);
        
        // 模拟网关状态更新
        scheduler.scheduleAtFixedRate(this::simulateGatewayStatusUpdate, 15, 60, TimeUnit.SECONDS);
        
        log.info("Status simulation started");
    }
    
    /**
     * 尝试连接到FreeSWITCH
     */
    private void tryConnectToFreeSwitch() {
        scheduler.schedule(() -> {
            try {
                log.info("Attempting to connect to FreeSWITCH at {}:{}", host, port);
                
                // 这里应该实现真实的ESL连接
                // 由于API问题，暂时使用模拟数据
                log.info("FreeSWITCH connection simulation - using mock data");
                
            } catch (Exception e) {
                log.error("Error connecting to FreeSWITCH", e);
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 模拟用户状态更新
     */
    private void simulateUserStatusUpdate() {
        try {
            // 模拟一些用户状态
            String[] userIds = {"1001", "1002", "1003", "1004", "1005"};
            UserStatus.Status[] statuses = {
                UserStatus.Status.IDLE, 
                UserStatus.Status.BRIDGE, 
                UserStatus.Status.RINGING,
                UserStatus.Status.ANSWERED,
                UserStatus.Status.HANGUP
            };
            
            for (String userId : userIds) {
                UserStatus.Status randomStatus = statuses[(int) (Math.random() * statuses.length)];
                
                UserStatus userStatus = UserStatus.builder()
                    .userId(userId)
                    .status(randomStatus.name())
                    .statusDescription(randomStatus.getDescription())
                    .channelUuid("channel-" + System.currentTimeMillis())
                    .destinationNumber("2000")
                    .callerNumber(userId)
                    .updateTime(LocalDateTime.now())
                    .build();
                
                userStatusCache.put(userId, userStatus);
                userStatusService.updateUserStatus(userStatus);
            }
            
            log.info("Simulated user status update for {} users", userIds.length);
            
        } catch (Exception e) {
            log.error("Error simulating user status update", e);
        }
    }
    
    /**
     * 模拟网关状态更新
     */
    private void simulateGatewayStatusUpdate() {
        try {
            // 模拟一些网关状态
            String[] gatewayNames = {"gateway1", "gateway2", "gateway3"};
            GatewayStatus.Status[] statuses = {
                GatewayStatus.Status.REGISTED, 
                GatewayStatus.Status.UNREGISTED, 
                GatewayStatus.Status.FAILED
            };
            
            for (String gatewayName : gatewayNames) {
                GatewayStatus.Status randomStatus = statuses[(int) (Math.random() * statuses.length)];
                
                GatewayStatus gatewayStatus = GatewayStatus.builder()
                    .gatewayName(gatewayName)
                    .status(randomStatus.name())
                    .statusDescription(randomStatus.getDescription())
                    .currentSessions((int) (Math.random() * 10))
                    .maxSessions(100)
                    .tryAttempts(100)
                    .successAttempts(randomStatus == GatewayStatus.Status.REGISTED ? 95 : 0)
                    .failAttempts(randomStatus == GatewayStatus.Status.REGISTED ? 5 : 100)
                    .lastRegisterTime(LocalDateTime.now().minusMinutes((long) (Math.random() * 60)))
                    .updateTime(LocalDateTime.now())
                    .build();
                
                gatewayStatusCache.put(gatewayName, gatewayStatus);
                gatewayStatusService.updateGatewayStatus(gatewayStatus);
            }
            
            log.info("Simulated gateway status update for {} gateways", gatewayNames.length);
            
        } catch (Exception e) {
            log.error("Error simulating gateway status update", e);
        }
    }
    
    /**
     * 获取用户状态
     */
    public UserStatus getUserStatus(String userId) {
        return userStatusCache.get(userId);
    }
    
    /**
     * 获取网关状态
     */
    public GatewayStatus getGatewayStatus(String gatewayName) {
        return gatewayStatusCache.get(gatewayName);
    }
    
    /**
     * 销毁方法
     */
    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("FreeSwitchEventListener destroyed");
    }
}
