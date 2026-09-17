package com.chandler.fcc.fcc.client.dto;

import com.chandler.fcc.common.entity.FNodeResult;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class JsonRpcResponse implements Serializable {
    private String jsonrpc;
    private Object id;
    private FNodeResult result;
    private JsonRpcError error;

    @Getter
    @Setter
    @ToString
    public static class JsonRpcError implements Serializable {
        private Integer code;
        private String message;
        private Object data;
    }
}
