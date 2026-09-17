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
 * DTMF 按键记录明细表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dtmf_record")
public class DtmfRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 call_session ID */
    private Long sessionId;

    /** 通道 UUID */
    private String channelUuid;

    /** 按键分机号 */
    private String extension;

    /** 按下的按键值 (0-9, *, #) */
    private String digit;

    /** 按键持续时长 (毫秒) */
    private Integer durationMs;

    /** 所在业务环节 (如 WELCOME_MENU, SATISFACTION) */
    private String stepName;

    /** 按键时间 */
    private Date createdAt;
}
