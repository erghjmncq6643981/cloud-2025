package com.chandler.freeswitch.client.example.domain;

import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 呼叫中心呼叫会话与通道 Leg 状态机流转单元测试
 * 覆盖：会话初始化、振铃、应答桥接、挂断原因与状态转换
 */
@DisplayName("CallSession & CallLeg 呼叫状态机流转测试")
public class CallSessionStateMachineTest {

    @Test
    @DisplayName("测试双呼桥接会话生命周期状态转换 (0-呼叫中 -> 1-通话中 -> 2-已挂断)")
    void testCallSessionLifecycleTransitions() {
        String bizId = "test-session-" + UUID.randomUUID();
        Date startTime = new Date();

        // 1. 初始化会话 (Direction: 3-双呼, Status: 0-呼叫中)
        CallSession session = CallSession.builder()
                .id(1001L)
                .bizId(bizId)
                .direction(3)
                .caller("1007")
                .callee("1008")
                .status(0)
                .startTime(startTime)
                .build();

        assertNotNull(session);
        assertEquals(0, session.getStatus(), "初始状态应为 0-呼叫中");
        assertEquals("1007", session.getCaller());
        assertEquals("1008", session.getCallee());

        // 2. 状态转换为 1-通话中 (桥接成功)
        session.setStatus(1);
        assertEquals(1, session.getStatus(), "桥接后状态应为 1-通话中");

        // 3. 状态转换为 2-已挂断 (正常挂机)
        session.setStatus(2);
        session.setEndTime(new Date(startTime.getTime() + 45000)); // 通话 45 秒
        session.setHangupReason("NORMAL_CLEARING");

        assertEquals(2, session.getStatus(), "挂断后状态应为 2-已挂断");
        assertEquals("NORMAL_CLEARING", session.getHangupReason());
        assertTrue(session.getEndTime().after(session.getStartTime()), "结束时间应晚于开始时间");
    }

    @Test
    @DisplayName("测试双腿 (Leg-A & Leg-B) 独立通道状态演进")
    void testCallLegStateMachine() {
        Long sessionId = 1002L;
        String uuidA = UUID.randomUUID().toString();
        String uuidB = UUID.randomUUID().toString();

        // Leg-A 主叫通道
        CallLeg legA = CallLeg.builder()
                .id(1L)
                .sessionId(sessionId)
                .legType("a-leg")
                .uuid(uuidA)
                .extension("1007")
                .status("CS_NEW")
                .build();

        // Leg-B 被叫通道
        CallLeg legB = CallLeg.builder()
                .id(2L)
                .sessionId(sessionId)
                .legType("b-leg")
                .uuid(uuidB)
                .extension("1008")
                .status("CS_NEW")
                .build();

        assertEquals("a-leg", legA.getLegType());
        assertEquals("b-leg", legB.getLegType());

        // 双方进入 Park 状态等待桥接
        legA.setStatus("CS_PARK");
        legB.setStatus("CS_PARK");
        assertEquals("CS_PARK", legA.getStatus());
        assertEquals("CS_PARK", legB.getStatus());

        // 执行 Bridge 桥接
        legA.setStatus("CS_EXCHANGE_MEDIA");
        legB.setStatus("CS_EXCHANGE_MEDIA");
        assertEquals("CS_EXCHANGE_MEDIA", legA.getStatus());

        // 主叫挂机，触发挂机清理
        legA.setStatus("CS_HANGUP");
        legB.setStatus("CS_HANGUP");
        assertEquals("CS_HANGUP", legA.getStatus());
        assertEquals("CS_HANGUP", legB.getStatus());
    }

    @Test
    @DisplayName("测试未接通异常状态流转 (Status: 3-未接通)")
    void testUnansweredCallSession() {
        CallSession session = CallSession.builder()
                .id(1003L)
                .bizId("timeout-call")
                .direction(1) // 呼入
                .caller("13800138000")
                .callee("02160882100")
                .status(0)
                .startTime(new Date())
                .build();

        // 模拟超时未应答 (NO_ANSWER / USER_BUSY)
        session.setStatus(3);
        session.setHangupReason("NO_ANSWER");
        session.setEndTime(new Date());

        assertEquals(3, session.getStatus(), "未接通会话状态应为 3");
        assertEquals("NO_ANSWER", session.getHangupReason());
    }
}
