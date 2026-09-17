package com.chandler.fcc.common.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.*;

import java.io.Serializable;

/**
 * FNode JSON-RPC 2.0 响应模型
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeResult implements Serializable {
    @JSONField(name = "node_id", alternateNames = {"nodeId"})
    private String nodeId;
    @Builder.Default
    private Integer code = 200;
    @Builder.Default
    private String message = "OK";
    private String uuid;
    @JSONField(name = "ctrl_uuid", alternateNames = {"ctrlUuid"})
    private String ctrlUuid;
    private String cause;
    private String dtmf;
    private String state;
    private Long activeChannels;
    private Integer maxChannels;
    private Long uptimeSeconds;
    private Object data;

    public boolean isSuccess() {
        return code != null && (code == 200 || code == 202);
    }
}
