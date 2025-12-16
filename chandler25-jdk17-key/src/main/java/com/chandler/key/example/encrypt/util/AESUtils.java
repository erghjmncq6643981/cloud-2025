package com.chandler.key.example.encrypt.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
public class AESUtils {
    public enum AESMode {
        ECB("AES/ECB/PKCS5Padding"),
        CBC("AES/CBC/PKCS5Padding"),
        GCM("AES/GCM/NoPadding");

        private final String transformation;

        AESMode(String transformation) {
            this.transformation = transformation;
        }

        public String getTransformation() {
            return transformation;
        }
    }

    /**
     * 生成 AES 密钥
     */
    public static SecretKey generateKey(int keySize) throws Exception {
        if (keySize != 128 && keySize != 192 && keySize != 256) {
            throw new IllegalArgumentException("Key size must be 128, 192, or 256 bits");
        }

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(keySize, SecureRandom.getInstanceStrong());
        return keyGen.generateKey();
    }

    /**
     * AES 加密
     */
    public static String encrypt(String plaintext, String key, AESMode mode) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        return encrypt(plaintext, secretKey, mode);
    }

    /**
     * AES 加密
     */
    public static String encrypt(String plaintext, SecretKey key, AESMode mode) throws Exception {
        Cipher cipher = Cipher.getInstance(mode.getTransformation());

        byte[] iv = null;
        switch (mode) {
            case CBC:
                // 生成随机 IV
                iv = new byte[16];
                SecureRandom secureRandom = new SecureRandom();
                secureRandom.nextBytes(iv);
                cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
                break;
            case GCM:
                // GCM 模式需要 IV
                iv = new byte[12];
                SecureRandom gcmRandom = new SecureRandom();
                gcmRandom.nextBytes(iv);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
                break;
            default:
                // ECB 模式不需要 IV
                cipher.init(Cipher.ENCRYPT_MODE, key);
        }

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(
                StandardCharsets.UTF_8));

        // 对于需要 IV 的模式，将 IV 和密文合并
        if (mode == AESMode.CBC || mode == AESMode.GCM) {
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        }

        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES 解密
     */
    public static String decrypt(String encryptedText, String key, AESMode mode) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        return decrypt(encryptedText, secretKey, mode);
    }

    /**
     * AES 解密
     */
    public static String decrypt(String encryptedText, SecretKey key, AESMode mode) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

        Cipher cipher = Cipher.getInstance(mode.getTransformation());

        switch (mode) {
            case CBC:
                // 分离 IV 和密文
                byte[] ivCBC = new byte[16];
                byte[] ciphertextCBC = new byte[encryptedBytes.length - 16];
                System.arraycopy(encryptedBytes, 0, ivCBC, 0, 16);
                System.arraycopy(encryptedBytes, 16, ciphertextCBC, 0, ciphertextCBC.length);
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivCBC));
                return new String(cipher.doFinal(ciphertextCBC), StandardCharsets.UTF_8);

            case GCM:
                // 分离 IV 和密文
                byte[] ivGCM = new byte[12];
                byte[] ciphertextGCM = new byte[encryptedBytes.length - 12];
                System.arraycopy(encryptedBytes, 0, ivGCM, 0, 12);
                System.arraycopy(encryptedBytes, 12, ciphertextGCM, 0, ciphertextGCM.length);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, ivGCM);
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
                return new String(cipher.doFinal(ciphertextGCM), StandardCharsets.UTF_8);

            default:
                // ECB 模式
                cipher.init(Cipher.DECRYPT_MODE, key);
                return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        }
    }

    /**
     * 测试不同模式
     */
    public static void main(String[] args) throws Exception {
        String plaintext = "18878920013";
//        SecretKey key = generateKey(128);
//        String keyBase64 = Base64.getEncoder().encodeToString(key.getEncoded());
//
//        System.out.println("原始文本: " + plaintext);
//        System.out.println("密钥: " + keyBase64);
//        System.out.println("密钥Base64: " + keyBase64);
//        System.out.println();

        String encrypted = "J0Idse4iTbsnrNUUSgUBEWatD+rJk/rafh2eTjB72V25JxsL";
        String key = "5Gpl5F5+PiAnpDZdKxqQ+Q==";
        String ciphertext = decrypt(encrypted, key, AESMode.GCM);
        String cipherBase64 = Base64.getEncoder().encodeToString(ciphertext.getBytes(StandardCharsets.UTF_8));
        System.out.println(cipherBase64);
        ciphertext = new String(Base64.getDecoder().decode(cipherBase64), StandardCharsets.UTF_8);
        System.out.println(ciphertext);


    }
}
