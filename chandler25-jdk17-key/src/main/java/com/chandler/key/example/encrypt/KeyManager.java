package com.chandler.key.example.encrypt;

import com.chandler.key.example.encrypt.annotation.EncryptField;
import com.chandler.key.example.encrypt.encryptor.Encryptor;
import com.chandler.key.example.encrypt.enu.KeyType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
@Slf4j
public class KeyManager {
    private final static String PREFIX = "encrypt";
    private final Map<String, String> currentKeys = new HashMap<>();
    private final Map<String, String> oldKeys = new HashMap<>();

    private String algorithm = "DEF";
    private String key = "5Gpl5F5+PiAnpDZdKxqQ+Q==";
    // 旧密钥(用于密钥轮换过渡期)
    private String oldKey = "3t2QBbtBQ8kOGwa+H69S8A==";
    private Encryptor encryptor;

    @PostConstruct
    public void init() {
        // 校验密钥是否配置
        if (StringUtils.isEmpty(key)) {
            throw new IllegalArgumentException("加密密钥未配置");
        }
        // 初始化当前密钥
        currentKeys.put(KeyType.DEF.name(), key);
        // 初始化旧密钥(如果存在)
        if (!StringUtils.isEmpty(oldKey)) {
            oldKeys.put(KeyType.DEF.name(), oldKey);
        }

        // 初始化加密算法
        this.encryptor = EncryptorFactory.getEncryptor(algorithm);
    }

    /**
     * 加密
     *
     * @param plainText 明文
     * @return 密文
     */
    public String encrypt(String plainText) {
        if (StringUtils.isBlank(plainText)) {
            return plainText;
        }
        return encrypt(plainText, KeyType.DEF);
    }

    /**
     * 加密
     *
     * @param plainText 明文
     * @param keyType   密钥类型
     * @return 密文
     */
    public String encrypt(String plainText, KeyType keyType) {
        if (StringUtils.isBlank(plainText)) {
            return plainText;
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }
        return PREFIX + encryptor.encrypt(plainText, getCurrentKey(keyType));
    }

    /**
     * 解密方法(先尝试当前密钥，失败则尝试旧密钥)
     *
     * @param cipherText 密文
     * @return 明文
     */
    public String decrypt(String cipherText) {
        if (StringUtils.isBlank(cipherText)) {
            return cipherText;
        }

        return decrypt(cipherText, KeyType.DEF);
    }

    /**
     * 解密方法(先尝试当前密钥，失败则尝试旧密钥)
     *
     * @param cipherText 密文
     * @param keyType    密钥类型
     * @return 明文
     */
    public String decrypt(String cipherText, KeyType keyType) {
        if (StringUtils.isBlank(cipherText)) {
            return cipherText;
        }

        if (!isEncrypted(cipherText)) {
            return cipherText;
        }
        //去除加密标识
        cipherText = cipherText.substring(PREFIX.length());

        try {
            // 先用当前密钥解密
            return encryptor.decrypt(cipherText, getCurrentKey(keyType));
        } catch (Exception e) {
            // 当前密钥解密失败，尝试旧密钥
            String oldKey = getOldKey(keyType);
            if (oldKey != null) {
                return encryptor.decrypt(cipherText, oldKey);
            }
            // 无旧密钥或旧密钥也解密失败，抛出异常
            throw e;
        }
    }

    /**
     * 判断是否已经加密过了，加密过了不能再次加密。
     *
     * @param value 值
     * @return true：已加密；false：未加密
     */
    public boolean isEncrypted(String value) {
        return value.startsWith(PREFIX);
    }

    /**
     * 数据脱敏
     *
     * @param value        原始数据
     * @param prefixLength 保留的前缀
     * @param suffixLength 保留的后缀
     * @return 脱敏之后的数据
     */
    public String mask(String value, int prefixLength, int suffixLength) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        EncryptField encryptField = new EncryptField() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return EncryptField.class;
            }

            @Override
            public KeyType type() {
                return KeyType.DEF;
            }

            @Override
            public int prefixLength() {
                return prefixLength;
            }

            @Override
            public int suffixLength() {
                return suffixLength;
            }
        };

        return mask(value, encryptField);
    }

    /**
     * 数据脱敏
     *
     * @param value        原始数据
     * @param encryptField 脱敏策略
     * @return 脱敏之后的数据
     */
    public String mask(String value, EncryptField encryptField) {
        if (StringUtils.isBlank(value)) {
            return value;
        }

        int prefixLen = 0;
        int suffixLen = 0;
        switch (encryptField.type()) {
            case DEF:
            case ID_CARD:
            case PHONE_NUMBER:
            case BANK_CARD:
                prefixLen = (encryptField.prefixLength() > 0) ? encryptField.prefixLength() : 3;
                suffixLen = (encryptField.suffixLength() > 0) ? encryptField.suffixLength() : 4;
                // 保留前缀和后缀，中间用*替代
                return maskMiddle(value, prefixLen, suffixLen);
            case NAME:
                // 姓名: 仅显示姓，其他用*代替
                prefixLen = (encryptField.prefixLength() > 0) ? encryptField.prefixLength() : 1;
                return maskMiddle(value, prefixLen, suffixLen);
            case ADDRESS:
                // 地址: 保留前缀，其余用*替代
                prefixLen = Math.max(encryptField.prefixLength(), 0);
                return maskMiddle(value, prefixLen, suffixLen);
            case EMAIL:
                prefixLen = Math.max(encryptField.prefixLength(), 0);
                suffixLen = Math.max(encryptField.suffixLength(), 0);
                // 邮箱: 分开处理@前后的部分
                int atIndex = value.indexOf('@');
                if (atIndex > 0) {
                    int localPartLen = Math.min(prefixLen, atIndex);
                    return value.substring(0, localPartLen) +
                            "*".repeat(atIndex - localPartLen) +
                            value.substring(atIndex);
                }
                return maskMiddle(value, prefixLen, suffixLen);

            default:
                return maskMiddle(value, prefixLen, suffixLen);
        }
    }

    private String maskMiddle(String value, int prefixLen, int suffixLen) {
        int len = value.length();

        if (len <= prefixLen + suffixLen) {
            return value;
        }
        String prefix = value.substring(0, prefixLen);
        String suffix = value.substring(len - suffixLen);
        return prefix + "*".repeat(len - prefixLen - suffixLen) + suffix;
    }

    public String getCurrentKey(KeyType keyType) {
        return currentKeys.get(keyType.name());
    }

    public String getOldKey(KeyType keyType) {
        return oldKeys.get(keyType.name());
    }

}
