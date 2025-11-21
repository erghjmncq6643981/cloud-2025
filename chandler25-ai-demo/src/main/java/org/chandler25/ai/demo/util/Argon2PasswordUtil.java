package org.chandler25.ai.demo.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 17:08
 * @version 1.0.0
 * @since 21
 */
public class Argon2PasswordUtil {
    private final static Argon2 argon2 = Argon2Factory.create();

    // 参数配置：迭代次数、内存使用、并行度
    private static final int ITERATIONS = 10;
    private static final int MEMORY = 65536; // 64MB
    private static final int PARALLELISM = 4;

    public static String hashPassword(String password) {
        try {
            return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, password.toCharArray());
        } finally {
            argon2.wipeArray(password.toCharArray());
        }
    }

    public static boolean verifyPassword(String password, String hash) {
        return argon2.verify(hash, password.toCharArray());
    }

    public static void main(String[] args) {
        System.out.println(hashPassword("123456"));
//        System.out.println(verifyPassword("123456","$argon2i$v=19$m=65536,t=10,p=4$TtSfSQjIJ8ujYxZ77GDa6w$OWXW21uAAFDt+k+BZEbBBI19DKwAO6uA1LibPGbLZCs"));
    }
}