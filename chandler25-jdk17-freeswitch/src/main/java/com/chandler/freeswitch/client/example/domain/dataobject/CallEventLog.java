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
 * ESL 原始事件审计日志表 (排障黑匣子)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("call_event_log")
public class CallEventLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 call_session ID */
    private Long sessionId;

    /** 关联业务ID */
    private String bizId;

    /** 通道 UUID */
    private String channelUuid;

    /** 事件名称 (如 CHANNEL_ANSWER, CHANNEL_HANGUP) */
    private String eventName;

    /** 自定义子类 (如 sofia::register) */
    private String eventSubclass;

    /** 事件产生时间 */
    private Date eventTime;

    /** 挂断原因 */
    private String hangupCause;

    /** 核心 Header 集合 (JSON) */
    private String rawHeaders;

    /** 落库时间 */
    private Date createdAt;
}
