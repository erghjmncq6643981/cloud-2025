package com.chandler.fcc.common.util;

import java.util.UUID;

public class IdUtil {
    public static String getUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getCallUuid() {
        return "call-" + UUID.randomUUID().toString().substring(0, 16);
    }

    public static String getCtrlUuid(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
