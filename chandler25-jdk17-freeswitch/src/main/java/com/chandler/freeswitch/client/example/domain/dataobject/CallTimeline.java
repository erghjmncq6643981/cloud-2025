package com.chandler.freeswitch.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 通话业务时间线表 (结构化测试轨迹回放)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("call_timeline")
public class CallTimeline implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 call_session ID */
    private Long sessionId;

    /** 关联业务ID */
    private String bizId;

    /** 关联 call_leg ID (若与单腿相关) */
    private Long legId;

    /** 通道 UUID */
    private String channelUuid;

    /** 节点类型 (SESSION, LEG, COMMAND, MEDIA) */
    private String nodeType;

    /** 时间线事件类型 (如 RINGING, ANSWERED, BRIDGED, HANGUP) */
    private String eventType;

    /** 人类可读描述 (如: 1007分机应答，等待对端接听) */
    private String eventDesc;

    /** 事件发生时间 */
    private Date eventTime;

    /** 距离上一节点的耗时(毫秒) */
    private Integer durationMs;

    /** 创建时间 */
    private Date createdAt;
}
