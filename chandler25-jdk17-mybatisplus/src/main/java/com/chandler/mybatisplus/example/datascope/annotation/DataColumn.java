package com.chandler.mybatisplus.example.datascope.annotation;

import java.lang.annotation.*;

/**
 *
 *
 * @author 钱丁君-chandler 2025/12/26
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataColumn {

    /**
     * 表别名
     *
     * @return 别名
     */
    String alias() default "";

    /**
     * 字段名称
     *
     * @return 字段名称
     */
    String name() default "";

    /**
     * 字段数据类型
     * @return 字段数据类型
     */
    Class<?> javaClass() default Long.class;


}
