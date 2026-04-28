package com.chandler.freeswitch.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 通话Leg记录表
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("call_leg")
public class CallLeg extends CallCenterBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 call_session 的 ID */
    private Long sessionId;

    /** Leg类型: a-leg, b-leg */
    private String legType;

    /** FreeSWITCH 通道唯一ID */
    private String uuid;

    /** 分机号 */
    private String extension;

    /** 网关名称 */
    private String gateway;

    /** 通道状态: CS_NEW, CS_PARK, CS_HANGUP等 */
    private String status;
}
