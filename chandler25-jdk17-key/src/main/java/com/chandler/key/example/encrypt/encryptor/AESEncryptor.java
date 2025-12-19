package com.chandler.key.example.encrypt.encryptor;

import com.chandler.key.example.encrypt.util.AESUtils;
import com.chandler.key.example.encrypt.exception.DataEncryptException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
@Slf4j
public class AESEncryptor implements Encryptor {
    private final AESUtils.AESMode aesMode = AESUtils.AESMode.GCM;

    @Override
    public String encrypt(String plaintext, String key) {
        if (StringUtils.isEmpty(plaintext)) {
            return plaintext;
        }
        validateKey(key);

        try {
            return AESUtils.encrypt(plaintext, key, aesMode);
        } catch (Exception e) {
            log.error("数据加密失败", e);
            throw new DataEncryptException("加密操作异常", e);
        }
    }

    @Override
    public String decrypt(String ciphertext, String key) {
        if (StringUtils.isEmpty(ciphertext)) {
            return ciphertext;
        }
        try {
            return AESUtils.decrypt(ciphertext, key, aesMode);
        } catch (Exception e) {
            log.error("数据解密失败", e);
            throw new DataEncryptException("解密操作异常", e);
        }
    }

    private void validateKey(String key) {
        int keyLength = key.getBytes().length;
        if (keyLength != 24 && keyLength != 36 && keyLength != 48) {
            throw new IllegalArgumentException("AES密钥长度必须为24/36/48字节");
        }
    }

}
