package com.chandler.freeswitch.client.example.domain.dataobject;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 网关状态实体
 *
 * @author chandler
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayStatus {
    
    /**
     * 网关名称
     */
    private String gatewayName;
    
    /**
     * 网关状态
     */
    private String status;
    
    /**
     * 状态描述
     */
    private String statusDescription;
    
    /**
     * 当前会话数
     */
    private Integer currentSessions;
    
    /**
     * 最大会话数
     */
    private Integer maxSessions;
    
    /**
     * 尝试注册次数
     */
    private Integer tryAttempts;
    
    /**
     * 成功注册次数
     */
    private Integer successAttempts;
    
    /**
     * 失败注册次数
     */
    private Integer failAttempts;
    
    /**
     * 最后注册时间
     */
    private LocalDateTime lastRegisterTime;
    
    /**
     * 状态更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 注册状态
     */
    private String registerStatus;
    
    /**
     * Ping状态
     */
    private String pingStatus;
    
    /**
     * Ping时间
     */
    private String pingTime;
    
    /**
     * 网络IP地址
     */
    private String networkIp;
    
    /**
     * 网络端口
     */
    private String networkPort;
    
    /**
     * 配置文件
     */
    private String profile;
    
    /**
     * 域名
     */
    private String domain;
    
    /**
     * 源域名
     */
    private String fromDomain;
    
    /**
     * 目标域名
     */
    private String toDomain;
    
    /**
     * 子状态
     */
    private String substate;
    
    /**
     * 操作原因
     */
    private String reason;
    
    /**
     * 状态枚举
     */
    public enum Status {
        REGISTED("已注册"),
        UNREGISTED("未注册"),
        REGISTERING("注册中"),
        UNREGISTERING("注销中"),
        FAILED("注册失败"),
        NO_RESPONSE("无响应"),
        TIMEOUT("超时"),
        UNKNOWN("未知");
        
        private final String description;
        
        Status(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
