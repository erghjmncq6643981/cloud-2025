package com.chandler.security.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要签名验证注解
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSignature {
    
    /**
     * 是否必须验证签名
     */
    boolean required() default true;
    
    /**
     * 自定义时间窗口（秒）
     */
    long timestampWindow() default -1;
}