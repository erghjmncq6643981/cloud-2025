package com.chandler.fcc.fcc.client;

import com.alibaba.fastjson2.JSON;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.fcc.client.dto.*;
import com.chandler.fcc.fcc.config.FccProperties;
import io.nats.client.Connection;
import io.nats.client.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FCC 核心客户端：负责向 FNode (Go Sidecar) 发起标准 JSON-RPC 2.0 请求
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FccClient {

    private final Connection natsConnection;
    private final FccProperties fccProperties;

    public FNodeResult dial(FNodeDialDTO dto) {
        return sendRequest("FNode.Dial", dto);
    }

    public FNodeResult channelBridge(String ctrlUuid, String uuid, String peerUuid) {
        FNodeBridgeDTO dto = FNodeBridgeDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .peerUuid(peerUuid)
                .build();
        return sendRequest("FNode.ChannelBridge", dto);
    }

    public FNodeResult readDTMF(FNodeReadDTMFDTO dto) {
        return sendRequest("FNode.ReadDTMF", dto);
    }

    public FNodeResult play(FNodePlayDTO dto) {
        return sendRequest("FNode.Play", dto);
    }

    public FNodeResult record(FNodeRecordDTO dto) {
        return sendRequest("FNode.Record", dto);
    }

    public FNodeResult hangup(String ctrlUuid, String uuid, String cause) {
        FNodeHangupDTO dto = FNodeHangupDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .cause(cause != null ? cause : "NORMAL_CLEARING")
                .build();
        return sendRequest("FNode.Hangup", dto);
    }

    public FNodeResult nativeAPI(String cmd, String args) {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", cmd);
        params.put("args", args);
        return sendRequest("FNode.NativeAPI", params);
    }

    public FNodeResult status() {
        return sendRequest("FNode.Status", null);
    }

    /**
     * 发送同步 JSON-RPC 2.0 请求至指定 FNode 指令通道 (fs.cmd.{nodeId})
     */
    private FNodeResult sendRequest(String method, Object params) {
        String reqId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        JsonRpcRequest req = JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(reqId)
                .method(method)
                .params(params)
                .build();

        String subject = "fs.cmd." + fccProperties.getDefaultNodeId();
        byte[] payload = JSON.toJSONBytes(req);

        log.debug("📤 [FCC -> NATS] 发送指令 Subject: {}, Method: {}\nPayload: {}",
                subject, method, new String(payload, StandardCharsets.UTF_8));

        try {
            Message reply = natsConnection.request(subject, payload, Duration.ofMillis(fccProperties.getRpcTimeoutMillis()));
            if (reply == null) {
                log.error("❌ [FCC] RPC 请求超时 ({} ms), Method: {}", fccProperties.getRpcTimeoutMillis(), method);
                return FNodeResult.builder()
                        .code(-32000)
                        .message("RPC timeout")
                        .build();
            }

            String respStr = new String(reply.getData(), StandardCharsets.UTF_8);
            log.debug("📥 [NATS -> FCC] 收到应答: {}", respStr);

            JsonRpcResponse resp = JSON.parseObject(respStr, JsonRpcResponse.class);
            if (resp.getError() != null) {
                log.warn("⚠️ [FCC] 节点返回业务错误: code={}, msg={}",
                        resp.getError().getCode(), resp.getError().getMessage());
                return FNodeResult.builder()
                        .code(resp.getError().getCode())
                        .message(resp.getError().getMessage())
                        .data(resp.getError().getData())
                        .build();
            }

            return resp.getResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ [FCC] 请求被中断: {}", e.getMessage());
            return FNodeResult.builder().code(-32000).message("Interrupted").build();
        } catch (Exception e) {
            log.error("❌ [FCC] 请求通信异常: {}", e.getMessage(), e);
            return FNodeResult.builder().code(-32000).message(e.getMessage()).build();
        }
    }
}
