package com.chandler.fcc.fcc.client.dto;

import lombok.*;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class MediaInfo implements Serializable {
    private String type;   // "TEXT", "FILE"
    private String data;   // 文本内容或录音文件路径
    private String voice;  // 发音人 (如 "aiqi")
    private String engine; // 引擎 (如 "ali", "flite")
}
