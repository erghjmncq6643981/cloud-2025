/*
 * chandler25-jdk17-freeswitch
 * 2026/3/13 12:37
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.freeswitch.client.example.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/3/13 12:37
 * @version 1.0.0
 * @since 1.8
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createdAt", Date.class, new Date());
        this.strictInsertFill(metaObject, "updatedAt", Date.class, new Date());
        this.strictInsertFill(metaObject, "createBy", String.class, "SYSTEM");
        this.strictInsertFill(metaObject, "updateBy", String.class, "SYSTEM");
        this.strictInsertFill(metaObject, "createdBy", String.class, "SYSTEM");
        this.strictInsertFill(metaObject, "updatedBy", String.class, "SYSTEM");
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedAt", Date.class, new Date());
        this.strictUpdateFill(metaObject, "updateBy", String.class, "SYSTEM");
        this.strictUpdateFill(metaObject, "updatedBy", String.class, "SYSTEM");
    }
}
