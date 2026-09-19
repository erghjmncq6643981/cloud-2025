package com.chandler.fcc;

import com.chandler.fcc.action.DefaultActionExecutorsManager;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowCtrlRecord;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.controller.CallVerifyController;
import com.chandler.fcc.fcc.client.FccClient;
import com.chandler.fcc.fcc.client.SidecarAdminClient;
import com.chandler.fcc.fcc.client.dto.FNodePlayDTO;
import com.chandler.fcc.fcc.client.dto.FNodeReadDTMFDTO;
import com.chandler.fcc.fcc.client.dto.FNodeRecordDTO;
import com.chandler.fcc.fcc.client.dto.MediaInfo;
import com.chandler.fcc.flow.CallSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
public class ChandlerFccFlowTest {

    @Autowired
    private FccClient fccClient;

    @Autowired
    private SidecarAdminClient sidecarAdminClient;

    @Autowired
    private CallVerifyController callVerifyController;

    @Autowired
    private DefaultActionExecutorsManager handlersManager;

    @Autowired
    private CallSessionManager sessionManager;

    @Test
    @DisplayName("测试用例 1: FCC 链路连通性与节点健康状态探活")
    public void testFccStatus() {
        FNodeResult result = fccClient.status();
        log.info("测试探活结果: {}", result);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("HEALTHY", result.getState());
        assertNotNull(result.getNodeId());
    }

    @Test
    @DisplayName("测试用例 2: 指令/动作 + 流程双向外呼流转验证")
    public void testOutboundFlow() {
        Map<String, Object> resp = callVerifyController.triggerOutboundCall("mock-agent-9999", "mock-guest-8888", true);
        log.info("外呼触发响应: {}", resp);
        assertNotNull(resp);
        assertEquals(200, resp.get("code"));

        String callUuid = (String) resp.get("callUuid");
        String ctrlUuid = (String) resp.get("ctrlUuid");
        assertNotNull(callUuid);
        assertNotNull(ctrlUuid);

        // 验证会话管理器中成功登记了会话
        Optional<CallInfoBO> sessionOpt = sessionManager.getByCtrlUuid(ctrlUuid);
        assertTrue(sessionOpt.isPresent());
        assertEquals(FlowModelType.OUTBOUND_TWO_WAY_CALL.name(), sessionOpt.get().getModelKey());

        // 验证动作调度中心是否成功记录了步骤流水
        List<FlowCtrlRecord> records = handlersManager.getRecords(callUuid);
        log.info("获取到的流程审计记录数: {}", records.size());
        assertFalse(records.isEmpty());
        assertEquals("DIAL_AGENT", records.get(0).getStepType());
    }

    @Test
    @DisplayName("测试用例 3: FCC 底层 NativeAPI 透传版本查询")
    public void testNativeAPI() {
        FNodeResult result = fccClient.nativeAPI("version", "");
        log.info("NativeAPI 执行结果: {}", result);
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("测试用例 4: 客户呼入客服流程模拟与放音步骤验证")
    public void testInboundSimulateFlow() {
        Map<String, Object> resp = callVerifyController.simulateInboundCall("mock-guest-8888", "mock-agent-9999");
        log.info("模拟呼入触发响应: {}", resp);
        assertNotNull(resp);
        assertEquals(200, resp.get("code"));

        String callUuid = (String) resp.get("callUuid");
        String ctrlUuid = (String) resp.get("ctrlUuid");
        assertNotNull(callUuid);
        assertNotNull(ctrlUuid);

        // 验证会话登记
        Optional<CallInfoBO> sessionOpt = sessionManager.getByCtrlUuid(ctrlUuid);
        assertTrue(sessionOpt.isPresent());
        assertEquals(FlowModelType.INBOUND_CUSTOMER_SERVICE.name(), sessionOpt.get().getModelKey());

        // 验证第一步执行了 IVR 放音收号
        List<FlowCtrlRecord> records = handlersManager.getRecords(callUuid);
        assertFalse(records.isEmpty());
        assertEquals("READ_DTMF", records.get(0).getStepType());
    }

    @Test
    @DisplayName("测试用例 5: 会话管理器生命周期全流程测试")
    public void testSessionManagerLifecycle() {
        String testCtrl = "ctrl-test-" + System.currentTimeMillis();
        String testCall = "call-test-" + System.currentTimeMillis();
        String testAgentChan = "agent-chan-" + System.currentTimeMillis();

        CallInfoBO info = CallInfoBO.builder()
                .ctrlUuid(testCtrl)
                .callUuid(testCall)
                .agentChannelUuid(testAgentChan)
                .guestChannelUuid(testCall)
                .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name())
                .direction(DirectionType.outbound)
                .stageState(CallStageState.START)
                .build();

        sessionManager.registerSession(info);

        // 检查各种方式查询
        assertTrue(sessionManager.getByCtrlUuid(testCtrl).isPresent());
        assertTrue(sessionManager.getByChannelUuid(testAgentChan).isPresent());
        assertTrue(sessionManager.getByChannelUuid(testCall).isPresent());

        // 释放会话
        sessionManager.removeSession(testCtrl);
        assertTrue(sessionManager.getByCtrlUuid(testCtrl).isEmpty());
    }

    @Test
    @DisplayName("测试用例 6: FNode 指令协议接口调用校验 (Play / ReadDTMF / Record / Hangup)")
    public void testFccDirectRpcCommands() {
        String mockUuid = "mock-uuid-" + System.currentTimeMillis();

        // 1. Play
        FNodePlayDTO playDTO = FNodePlayDTO.builder()
                .uuid(mockUuid)
                .media(MediaInfo.builder().type("TEXT").data("测试播报").build())
                .build();
        FNodeResult playRes = fccClient.play(playDTO);
        log.info("Play 指令响应 (预期对不存在话道返回错误或异常处理): {}", playRes);
        assertNotNull(playRes);

        // 2. ReadDTMF
        FNodeReadDTMFDTO readDTO = FNodeReadDTMFDTO.builder()
                .uuid(mockUuid)
                .media(MediaInfo.builder().type("TEXT").data("请按键").build())
                .minDigits(1)
                .maxDigits(1)
                .timeout(3000)
                .build();
        FNodeResult readRes = fccClient.readDTMF(readDTO);
        log.info("ReadDTMF 指令响应: {}", readRes);
        assertNotNull(readRes);

        // 3. Record Stop
        FNodeRecordDTO recordDTO = FNodeRecordDTO.builder()
                .uuid(mockUuid)
                .action("STOP")
                .build();
        FNodeResult recRes = fccClient.record(recordDTO);
        log.info("Record 指令响应: {}", recRes);
        assertNotNull(recRes);

        // 4. Hangup
        FNodeResult hangupRes = fccClient.hangup(null, mockUuid, "NORMAL_CLEARING");
        log.info("Hangup 指令响应: {}", hangupRes);
        assertNotNull(hangupRes);
    }

    @Test
    @DisplayName("测试用例 7: Sidecar HTTP 同步分机开户、状态检查与销户闭环")
    public void testSidecarExtensionManagement() {
        // 1. 测试健康检查
        Map<String, Object> health = sidecarAdminClient.healthCheck();
        assertNotNull(health);
        assertEquals("UP", health.get("status"));
        assertEquals("HEALTHY", health.get("state"));
        assertEquals(Boolean.TRUE, health.get("fs_alive"));

        String testExt = "1098";

        // 2. 测试分机开户 (POST /api/v1/extensions)
        SidecarAdminClient.SidecarResponse createResp = sidecarAdminClient.createExtension(
                testExt, "PassWord@123", "default", "default"
        );
        log.info("开户响应: {}", createResp);
        assertNotNull(createResp);
        assertTrue(createResp.isSuccess());
        assertEquals(200, createResp.getCode());
        assertEquals(testExt, createResp.getExtension());

        // 3. 测试分机状态检查 (GET /api/v1/extensions)
        SidecarAdminClient.SidecarResponse checkResp = sidecarAdminClient.checkExtension(testExt);
        log.info("检查分机响应: {}", checkResp);
        assertNotNull(checkResp);
        assertTrue(checkResp.isSuccess());
        assertNotNull(checkResp.getData());
        assertEquals(Boolean.TRUE, checkResp.getData().get("exists"));

        // 4. 测试分机销户 (DELETE /api/v1/extensions)
        SidecarAdminClient.SidecarResponse deleteResp = sidecarAdminClient.deleteExtension(testExt);
        log.info("销户响应: {}", deleteResp);
        assertNotNull(deleteResp);
        assertTrue(deleteResp.isSuccess());
        assertEquals(200, deleteResp.getCode());

        // 5. 再次检查分机已被清理
        SidecarAdminClient.SidecarResponse checkAfterDelete = sidecarAdminClient.checkExtension(testExt);
        log.info("销户后检查响应: {}", checkAfterDelete);
        assertNotNull(checkAfterDelete);
        assertNotNull(checkAfterDelete.getData());
        assertEquals(Boolean.FALSE, checkAfterDelete.getData().get("exists"));
    }
}
