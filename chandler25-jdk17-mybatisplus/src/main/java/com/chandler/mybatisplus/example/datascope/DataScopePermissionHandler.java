package com.chandler.mybatisplus.example.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;

/**
 * 数据权限插件
 * @author 钱丁君-chandler 2025/12/26
 */
@Slf4j
@RequiredArgsConstructor
public class DataScopePermissionHandler implements MultiDataPermissionHandler {
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        //检查接口上是否有目标注解
        //检查表别名是否一致

        return null;
    }
}
