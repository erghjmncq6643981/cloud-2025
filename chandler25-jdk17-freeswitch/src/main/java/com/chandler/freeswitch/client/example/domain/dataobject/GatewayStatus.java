package com.chandler.freeswitch.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
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
@TableName("gateway_status")
public class GatewayStatus implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 网关名称 */
    private String gatewayName;

    /** 所属 Profile */
    private String profileName;

    /** 核心状态 (REGED, FAIL_WAIT 等) */
    private String state;

    private String statusDescription;

    private String hostname;

    // --- 审计与逻辑删除字段 ---

    /** 创建人，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除字段 (0:未删除, 1:已删除) */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
