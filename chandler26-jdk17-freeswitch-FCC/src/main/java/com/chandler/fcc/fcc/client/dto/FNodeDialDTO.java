package com.chandler.fcc.fcc.client.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeDialDTO implements Serializable {
    @JSONField(name = "ctrl_uuid")
    private String ctrlUuid;
    private String uuid;
    private Destination destination;
    private String ringback;
    private Boolean sync;
    private Integer timeout;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class Destination implements Serializable {
        @JSONField(name = "call_params")
        private List<CallParam> callParams;
        @JSONField(name = "global_params")
        private Map<String, String> globalParams;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class CallParam implements Serializable {
        @JSONField(name = "dial_string")
        private String dialString;
        @JSONField(name = "cid_name")
        private String cidName;
        @JSONField(name = "cid_number")
        private String cidNumber;
        private String uuid;
        private Map<String, String> params;
    }
}
