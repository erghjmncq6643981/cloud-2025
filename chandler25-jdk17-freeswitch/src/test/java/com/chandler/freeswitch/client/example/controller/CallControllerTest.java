package com.chandler.freeswitch.client.example.controller;

import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import com.chandler.freeswitch.client.example.listener.CallBridgeListener;
import com.chandler.freeswitch.client.example.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 呼叫控制业务控制器单元测试
 * 覆盖：双呼发起 (/api/call/bridge)、通道转接校验、录音控制与异常处理
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CallController 呼叫控制单元测试")
public class CallControllerTest {

    @Mock
    private FreeSwitchCommandGateway commandGateway;
    @Mock
    private UserStatusService userStatusService;
    @Mock
    private CallSessionService callSessionService;
    @Mock
    private CallLegService callLegService;
    @Mock
    private CommandLogService commandLogService;
    @Mock
    private CallBridgeListener callBridgeListener;
    @Mock
    private CallEventLogService callEventLogService;
    @Mock
    private CallTimelineService callTimelineService;
    @Mock
    private DtmfRecordService dtmfRecordService;
    @Mock
    private CallFileRecordService callFileRecordService;

    @InjectMocks
    private CallController callController;

    @BeforeEach
    void setUp() {
        FreeSwitchCommandResult mockResult = new FreeSwitchCommandResult("127.0.0.1:8021", "ASYNC", "originate", "arg", "+OK Job-UUID", "mock-uuid");
        when(commandGateway.originateUserToPark(anyString(), anyString(), anyMap())).thenReturn(mockResult);

        // Mock mybatis-plus save to simulate ID generation
        doAnswer(invocation -> {
            CallSession session = invocation.getArgument(0);
            session.setId(9901L);
            return true;
        }).when(callSessionService).save(any(CallSession.class));

        doAnswer(invocation -> {
            CallLeg leg = invocation.getArgument(0);
            leg.setId(101L);
            return true;
        }).when(callLegService).save(any(CallLeg.class));
    }

    @Test
    @DisplayName("测试双呼桥接接口 (/api/call/bridge) 成功创建会话并下发分机呼叫")
    void testBridgeCallSuccess() {
        String extA = "1007";
        String extB = "1008";

        Map<String, Object> response = callController.bridgeCall(extA, extB, true, true);

        assertNotNull(response);
        assertEquals(0, response.get("code"));
        assertNotNull(response.get("bizId"));
        assertTrue(response.get("bizId").toString().startsWith("bridge-"));

        // 验证持久化调用
        verify(callSessionService, times(1)).save(any(CallSession.class));
        verify(callLegService, times(2)).save(any(CallLeg.class));
        verify(callTimelineService, atLeastOnce()).record(any(), any(), any(), any(), any(), any(), any());

        // 验证向分机 A 和分机 B 发起呼叫进 Park
        verify(commandGateway, times(2)).originateUserToPark(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("测试转接接口 (/api/call/transfer) 无活动桥接通话时应优雅拒绝")
    void testTransferCallWithoutActiveBridge() {
        when(callBridgeListener.getBridgeContextBySessionId(anyLong())).thenReturn(null);
        when(callBridgeListener.getLatestActiveBridgeContext()).thenReturn(null);

        Map<String, Object> response = callController.transferCall(100L, "1009", 20);

        assertNotNull(response);
        assertEquals(400, response.get("code"));
        assertTrue(response.get("message").toString().contains("未找到处于通话中的双呼会话"));
    }
}
