package com.chandler.key.example.encrypt.encryptor;

import com.chandler.key.example.encrypt.util.RSAUtils;
import com.chandler.key.example.encrypt.exception.DataEncryptException;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
@Slf4j
public class RSAEncryptor implements Encryptor {
    private final RSAUtils.RSAPadding rsaPadding = RSAUtils.RSAPadding.OAEP_SHA256;

    @Override
    public String encrypt(String plainText, String publicKey) {
        try {
            return RSAUtils.encrypt(plainText, publicKey, rsaPadding);
        } catch (Exception e) {
            log.error("数据加密失败", e);
            throw new DataEncryptException("加密操作异常", e);
        }
    }

    @Override
    public String decrypt(String cipherText, String privateKey) {
        try {
            return RSAUtils.decrypt(cipherText, privateKey, rsaPadding);
        } catch (Exception e) {
            log.error("数据解密失败", e);
            throw new DataEncryptException("解密操作异常", e);
        }
    }
}
