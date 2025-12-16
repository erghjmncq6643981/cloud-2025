package com.chandler.key.example.encrypt.interceptor;

import com.chandler.key.example.encrypt.KeyManager;
import com.chandler.key.example.encrypt.annotation.EncryptEntity;
import com.chandler.key.example.encrypt.annotation.EncryptField;
import com.chandler.key.example.encrypt.exception.DataEncryptException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author 钱丁君-chandler 2025/12/16
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})})
@RequiredArgsConstructor
public class ColumnsEncryptInterceptor implements Interceptor {
    private final KeyManager keyManager;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //0.sql参数获取
        Object parameter = null;
        if (invocation.getArgs().length > 1) {
            parameter = invocation.getArgs()[1];
        }

        if (Objects.nonNull(parameter)) {
            if (parameter instanceof Map) {
                Map<String, Object> param = (Map) parameter;
                for (Map.Entry<String, Object> entry : param.entrySet()) {
                    encryptObj(entry.getValue());
                }
            } else {
                encryptObj(parameter);
            }
        }
        return invocation.proceed();
    }

    private void encryptObj(Object parameter) throws DataEncryptException {
        if (parameter == null) {
            return;
        }
        if (parameter instanceof Collection) {
            Collection<Object> collection = (Collection<Object>) parameter;
            for (Object o : collection) {
                encryptSingle(o);
            }
        } else {
            encryptSingle(parameter);
        }
    }

    private void encryptSingle(Object parameter) throws DataEncryptException {
        if (parameter == null) {
            return;
        }
        Class<?> parameterObjectClass = parameter.getClass();
        //检查对象是否有目标注解
        EncryptEntity encryptDecryptClass = AnnotationUtils.findAnnotation(parameterObjectClass, EncryptEntity.class);
        if (Objects.nonNull(encryptDecryptClass)) {
            //如果存在就加密
            encrypt(parameter);
        }
    }

    private void encrypt(Object parameterObject) throws DataEncryptException {
        if (parameterObject == null) {
            return;
        }
        try {
            List<Field> secretFields = FieldUtils.getFieldsListWithAnnotation(parameterObject.getClass(), EncryptField.class);
            if (!CollectionUtils.isEmpty(secretFields)) {
                for (Field secretField : secretFields) {
                    String plaintext = (String) FieldUtils.readField(secretField, parameterObject, true);
                    if (StringUtils.isEmpty(plaintext)) {
                        continue;
                    }
                    //检查是否已经加密
                    boolean encrypted = keyManager.isEncrypted(plaintext);
                    if (!encrypted) {
                        //数据没有加密则进行加密
                        String ciphertext = keyManager.encrypt(plaintext);
                        FieldUtils.writeField(secretField, parameterObject, ciphertext, true);
                    }
                }
            }
        } catch (Exception e) {
            log.error("encrypt failure", e);
            throw new DataEncryptException("encrypt failure", e);
        }
    }

}
