package com.chandler.fcc.fcc.client.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.*;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodePlayDTO implements Serializable {
    @JSONField(name = "ctrl_uuid")
    private String ctrlUuid;
    private String uuid;
    private MediaInfo media;
}
