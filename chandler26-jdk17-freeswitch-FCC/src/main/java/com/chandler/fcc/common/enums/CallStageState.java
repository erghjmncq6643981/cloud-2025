package com.chandler.fcc.common.enums;

/**
 * 呼叫生命周期阶段状态
 */
public enum CallStageState {
    START,       // 呼叫创建/来电进入
    CALLING,     // 外呼呼叫中
    RINGING,     // 振铃中
    ROUTE,       // 话道就绪，执行路由排队/分配坐席
    CONNECTED,   // 双方接通桥接中
    NORMAL_END,  // 正常挂机结束
    ERROR_END    // 异常/失败结束
}
