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
 * IVR流程与呼入路由策略配置表
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ivr_flow_config")
public class IvrFlowConfig extends CallCenterBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程唯一编码: FLOW-INBOUND / FLOW-OUTBOUND / FLOW-PHONE-DIRECT */
    private String flowCode;

    /** 流程名称: 如 "来电流程", "外呼", "话机直接外呼" */
    private String flowName;

    /** 呼入当前激活路由模式: DID_DIRECT / RULE_ENGINE / HTTP_CALLBACK */
    private String inboundRouteMode;

    /** 模式1: DID 直达配置参数 (JSON) */
    private String didDirectConfig;

    /** 模式2: 规则引擎配置参数 (JSON) */
    private String ruleEngineConfig;

    /** 模式3: 业务接口回调配置参数 (带熔断降级兜底) (JSON) */
    private String httpCallbackConfig;

    /** 状态: 1-启用, 0-禁用 */
    private Integer status;

    /** 版本号 (乐观锁) */
    private Integer version;
}
