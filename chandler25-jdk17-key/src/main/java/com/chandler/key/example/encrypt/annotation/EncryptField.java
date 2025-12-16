package com.chandler.key.example.encrypt.annotation;

import com.chandler.key.example.encrypt.enu.KeyType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要加密的字段
 * @author 钱丁君-chandler 2025/12/15
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptField {
    KeyType type() default KeyType.DEF;
    int prefixLength() default 0;
    int suffixLength() default 0;
}
