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
public class FNodeReadDTMFDTO implements Serializable {
    @JSONField(name = "ctrl_uuid")
    private String ctrlUuid;
    private String uuid;
    private MediaInfo media;
    @JSONField(name = "min_digits")
    private Integer minDigits;
    @JSONField(name = "max_digits")
    private Integer maxDigits;
    private Integer tries;
    private Integer timeout;
    @JSONField(name = "digit_timeout")
    private Integer digitTimeout;
    private String terminators;
    @JSONField(name = "thank_you_file")
    private String thankYouFile;
    private String regex;
    @JSONField(name = "action_after")
    private String actionAfter;
}
