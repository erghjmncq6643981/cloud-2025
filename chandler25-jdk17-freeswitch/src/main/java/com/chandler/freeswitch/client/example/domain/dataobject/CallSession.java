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

    /** 执行流程编码: FLOW-INBOUND / FLOW-OUTBOUND / FLOW-PHONE-DIRECT */
    private String flowCode;

    /** 呼入路由模式: DID_DIRECT / RULE_ENGINE / HTTP_CALLBACK */
    private String routeMode;

    /** 路由结果类型: AGENT / GROUP */
    private String routeTargetType;

    /** 路由命中目标: 坐席工号(如 901415) 或 技能组ID */
    private String routeTargetId;

    /** 最终服务坐席工号 */
    private String agentWorkNo;

    /** 最终服务坐席姓名 */
    private String agentName;

    /** 振铃等待耗时(毫秒) */
    private Integer ringDurationMs;

    /** 双轨录音时长(秒) */
    private Integer audioDurationSec;

    /** 客户满意度按键评分 (1-5星) */
    private Integer satisfactionScore;

    /** 关联双轨录音文件表ID */
    private Long recordFileId;
}
