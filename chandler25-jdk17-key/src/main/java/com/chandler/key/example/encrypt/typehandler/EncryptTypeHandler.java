package com.chandler.key.example.encrypt.typehandler;

import cn.hutool.extra.spring.SpringUtil;
import com.chandler.key.example.encrypt.KeyManager;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 加密字段处理
 * @author 钱丁君-chandler 2025/12/16
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EncryptTypeHandler extends BaseTypeHandler<String> {
    private final KeyManager keyManager;

    public EncryptTypeHandler() {
        this.keyManager = SpringUtil.getBean("keyManager", KeyManager.class);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        try {
            // 加密后存入数据库
            ps.setString(i, keyManager.encrypt(parameter));
        } catch (Exception e) {
            throw new SQLException("字段加密失败", e);
        }

    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return keyManager.decrypt(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return  keyManager.decrypt(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return  keyManager.decrypt(value);
    }
}
