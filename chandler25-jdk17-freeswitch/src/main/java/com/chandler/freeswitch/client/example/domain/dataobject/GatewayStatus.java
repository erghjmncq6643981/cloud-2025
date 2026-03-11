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
