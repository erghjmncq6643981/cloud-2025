package com.chandler.key.example.encrypt.interceptor;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.chandler.key.example.encrypt.KeyManager;
import com.chandler.key.example.encrypt.annotation.EncryptEntity;
import com.chandler.key.example.encrypt.annotation.EncryptField;
import com.chandler.key.example.encrypt.exception.DataEncryptException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Field;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author 钱丁君-chandler 2025/12/16
 */
@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
@RequiredArgsConstructor
public class ColumnsDecryptInterceptor implements Interceptor {
    private final KeyManager keyManager;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();

        if (Objects.isNull(result)) {
            return null;
        }

        if (result instanceof ArrayList) {
            ArrayList resultList = (ArrayList) result;
            if (CollectionUtils.isNotEmpty(resultList) && needToDecrypt(resultList.get(0))) {
                for (int i = 0; i < resultList.size(); i++) {
                    decryptSingle(resultList.get(i));
                }
            }
        } else {
            if (needToDecrypt(result)) {
                decryptSingle(result);
            }
        }
        return result;
    }

    private boolean needToDecrypt(Object object) {
        if (object == null) {
            return false;
        }
        Class<?> objectClass = object.getClass();
        EncryptEntity encryptDecryptClass = AnnotationUtils.findAnnotation(objectClass, EncryptEntity.class);
        if (Objects.nonNull(encryptDecryptClass)) {
            return true;
        }
        return false;
    }

    /**
     * 解密数据
     */
    private void decryptSingle(Object result) throws DataEncryptException {
        if (result == null) {
            return;
        }
        try {
            List<Field> secretFields = FieldUtils.getFieldsListWithAnnotation(result.getClass(), EncryptField.class);
            if (!CollectionUtils.isEmpty(secretFields)) {
                for (Field secretField : secretFields) {
                    String cipher = (String) FieldUtils.readField(secretField, result, true);
                    if (StringUtils.isEmpty(cipher)) {
                        continue;
                    }
                    boolean encrypted = keyManager.isEncrypted(cipher);
                    if (encrypted) {
                        //数据没有加密则进行加密
                        String plaintext = keyManager.decrypt(cipher);
                        EncryptField encryptField = secretField.getAnnotation(EncryptField.class);
                        String maskText = keyManager.mask(plaintext, encryptField);
                        FieldUtils.writeField(secretField, result, maskText, true);
                    }
                }
            }
        } catch (Exception e) {
            throw new DataEncryptException("decrypt failure", e);
        }
    }

}
