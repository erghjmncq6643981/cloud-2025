package com.chandler.freeswitch.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * 通话业务会话表
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("call_session")
public class CallSession extends CallCenterBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务系统关联ID */
    private String bizId;

    /** 呼叫方向: 1-呼入, 2-呼出 */
    private Integer direction;

    /** 主叫号码 */
    private String caller;

    /** 被叫号码 */
    private String callee;

    /** 状态: 0-呼叫中, 1-通话中, 2-已挂断, 3-未接通 */
    private Integer status;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 挂断原因 */
    private String hangupReason;
}
