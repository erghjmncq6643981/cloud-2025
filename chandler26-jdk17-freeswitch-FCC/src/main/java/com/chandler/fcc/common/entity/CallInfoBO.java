package com.chandler.fcc.common.entity;

import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

/**
 * 通话生命周期运行时业务上下文
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CallInfoBO implements Serializable {
    private String callUuid;
    private String ctrlUuid;
    private String modelKey;
    private DirectionType direction;
    private CallStageState stageState;
    private String callerNumber;
    private String destinationNumber;
    private String agentWorkNum;
    private String agentChannelUuid;
    private String guestChannelUuid;
    private ActionType actionType;
    private Integer duration;
    private Integer billsec;
    private String hangupCause;
    private Map<String, String> data;
}
