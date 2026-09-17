package com.chandler.fcc.common.entity;

import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

/**
 * 流程编排节点定义
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FlowNode implements Serializable {
    private String modelKey;
    private FlowModelType modelType;
    private CallStageState stageState;
    private String actionKey;
    private Integer order;
    private ActionType actionType;
    private Map<String, String> data;
}
