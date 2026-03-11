package com.chandler.freeswitch.client.example.domain.dataobject;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户状态实体
 *
 * @author chandler
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatus {
    
    /**
     * 用户ID/分机号
     */
    private String userId;
    
    /**
     * 用户状态
     */
    private String status;
    
    /**
     * 状态描述
     */
    private String statusDescription;
    
    /**
     * 当前通话UUID（如果有）
     */
    private String channelUuid;
    
    /**
     * 被叫号码（如果有）
     */
    private String destinationNumber;
    
    /**
     * 主叫号码（如果有）
     */
    private String callerNumber;
    
    /**
     * 状态更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 状态枚举
     */
    public enum Status {
        IDLE("空闲"),
        RINGING("振铃"),
        ANSWERED("已接听"),
        HANGUP("已挂断"),
        BRIDGE("通话中"),
        HOLD("保持"),
        PARKED("驻留"),
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
