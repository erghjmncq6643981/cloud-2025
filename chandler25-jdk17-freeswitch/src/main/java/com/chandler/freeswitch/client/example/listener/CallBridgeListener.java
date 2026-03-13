/*
 * chandler25-jdk17-freeswitch
 * 2026/3/13 13:56
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.freeswitch.client.example.listener;

import link.thingscloud.freeswitch.esl.InboundClient;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/3/13 13:56
 * @version 1.0.0
 * @since 1.8
 */
@Slf4j
@Component
@EslEventName({"CHANNEL_ANSWER", "CHANNEL_HANGUP"}) // 关键：监听应答和挂断
public class CallBridgeListener implements EslEventHandler {

    @Autowired
    private InboundClient inboundClient;

    // 使用本地缓存或 Redis 记录已经接通的通道信息
    private Map<String, Map<String, String>> taskMap = new ConcurrentHashMap<>();

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        // 1. 提取我们自定义的业务 ID
        String bizId = headers.get("variable_my_biz_id");
        if (bizId == null) return; // 非业务呼叫，忽略

        String uuid = headers.get("Unique-ID");
        String role = headers.get("variable_my_role");

        if ("CHANNEL_ANSWER".equals(eventName)) {
            log.info("🎯 业务 [{}] 的角色 [{}] 已接听, UUID: {}", bizId, role, uuid);
            handleAnswer(bizId, role, uuid);
        } else if ("CHANNEL_HANGUP".equals(eventName)) {
            taskMap.remove(bizId); // 任意一方挂断，清理任务
        }
    }

    private void handleAnswer(String bizId, String role, String uuid) {
        // 2. 将当前接通的 UUID 存入任务池
        Map<String, String> task = taskMap.computeIfAbsent(bizId, k -> new ConcurrentHashMap<>());
        task.put(role, uuid);

        // 3. 检查双方是否都已接通
        if (task.containsKey("caller") && task.containsKey("callee")) {
            String uuidA = task.get("caller");
            String uuidB = task.get("callee");

            log.info("🔗 双方已就绪，开始执行强制桥接: {} <-> {}", uuidA, uuidB);

            // 4. 下发最终桥接指令
            inboundClient.sendAsyncApiCommand("127.0.0.1:8022",
                    "uuid_bridge " + uuidA + " " + uuidB, null);

            taskMap.remove(bizId); // 桥接指令发出后清理缓存
        }
    }
}