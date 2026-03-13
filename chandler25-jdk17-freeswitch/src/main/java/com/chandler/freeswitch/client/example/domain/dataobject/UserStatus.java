package com.chandler.freeswitch.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
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
@TableName("user_status")
public class UserStatus implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String status;
    private String statusDescription;
    private String networkIp;
    private String userAgent;
    private String channelUuid;
    
    /** 通话状态: IDLE, RINGING, BUSY */
    private String callStatus;

    // --- 审计与逻辑删除字段 ---

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
