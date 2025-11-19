package org.chandler25.ai.demo.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/17 12:10
 * @version 1.0.0
 * @since 21
 */
public class KeyUtil {
    public static String getUuid() {
        return UUID.randomUUID().toString();
    }

    public static String getQuestionKey(){
        LocalDateTime today = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return getUuid()+formatter.format(today);
    }

    public static void main(String[] args) {
        System.out.println(getQuestionKey());
    }
}