package com.chandler.fcc.fcc.client.dto;

import lombok.*;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class JsonRpcRequest implements Serializable {
    @Builder.Default
    private String jsonrpc = "2.0";
    private Object id;
    private String method;
    private Object params;
}
