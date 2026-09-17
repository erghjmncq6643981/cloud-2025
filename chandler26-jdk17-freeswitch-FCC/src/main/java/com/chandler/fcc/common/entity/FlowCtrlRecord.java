package com.chandler.fcc.common.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程轨迹控制记录（可用于数据库落表或内存审计）
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FlowCtrlRecord implements Serializable {
    private String flowUuid;
    private String callUuid;
    private String stepKey;
    private String stepType;
    private Integer stepOrder;
    private String modelKey;
    private String stageState;
    private String detail;
    private LocalDateTime createTime;
}
