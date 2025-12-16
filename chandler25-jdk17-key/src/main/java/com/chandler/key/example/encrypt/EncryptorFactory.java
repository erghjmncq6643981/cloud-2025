package com.chandler.key.example.encrypt;

import com.chandler.key.example.encrypt.encryptor.AESEncryptor;
import com.chandler.key.example.encrypt.encryptor.Encryptor;
import com.chandler.key.example.encrypt.encryptor.RSAEncryptor;
import com.chandler.key.example.encrypt.encryptor.SM4Encryptor;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
public class EncryptorFactory {
    public static Encryptor getEncryptor(String algorithm) {
        return switch (algorithm.toUpperCase()) {
            case "DEF", "AES" -> new AESEncryptor();
            case "RSA" -> new RSAEncryptor();
            case "SM4" -> new SM4Encryptor();
            default -> throw new IllegalArgumentException("不支持的加密算法: " + algorithm);
        };
    }
}
