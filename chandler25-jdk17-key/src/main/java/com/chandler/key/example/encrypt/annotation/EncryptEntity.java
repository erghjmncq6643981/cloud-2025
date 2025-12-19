package com.chandler.key.example.encrypt.annotation;

import java.lang.annotation.*;

/**
 * 标记需要加密的对象
 * @author 钱丁君-chandler 2025/12/16
 */
@Documented
@Inherited
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptEntity {
}
