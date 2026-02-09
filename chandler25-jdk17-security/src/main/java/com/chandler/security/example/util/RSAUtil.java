package com.chandler.security.example.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * RSA加解密和数字签名工具类
 * 支持前端私钥加密，后端公钥解密的场景
 * 支持RSA数字签名验证
 */
public class RSAUtil {
    
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int KEY_SIZE = 2048;
    
    /**
     * 生成RSA密钥对
     * 
     * @return 密钥对
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("生成RSA密钥对失败", e);
        }
    }
    
    /**
     * 获取公钥的Base64编码字符串
     * 
     * @param keyPair 密钥对
     * @return 公钥Base64字符串
     */
    public static String getPublicKeyString(KeyPair keyPair) {
        PublicKey publicKey = keyPair.getPublic();
        return Base64.encode(publicKey.getEncoded());
    }
    
    /**
     * 获取私钥的Base64编码字符串
     * 
     * @param keyPair 密钥对
     * @return 私钥Base64字符串
     */
    public static String getPrivateKeyString(KeyPair keyPair) {
        PrivateKey privateKey = keyPair.getPrivate();
        return Base64.encode(privateKey.getEncoded());
    }
    
    /**
     * 从Base64字符串构建公钥
     * 
     * @param publicKeyStr 公钥Base64字符串
     * @return 公钥对象
     */
    public static PublicKey getPublicKeyFromString(String publicKeyStr) {
        try {
            byte[] keyBytes = Base64.decode(publicKeyStr);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("构建公钥失败", e);
        }
    }
    
    /**
     * 从Base64字符串构建私钥
     * 
     * @param privateKeyStr 私钥Base64字符串
     * @return 私钥对象
     */
    public static PrivateKey getPrivateKeyFromString(String privateKeyStr) {
        try {
            byte[] keyBytes = Base64.decode(privateKeyStr);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("构建私钥失败", e);
        }
    }
    
    /**
     * 使用私钥加密（前端使用）
     * 
     * @param data 待加密数据
     * @param privateKeyStr 私钥Base64字符串
     * @return 加密后的Base64字符串
     */
    public static String encryptWithPrivateKey(String data, String privateKeyStr) {
        if (StrUtil.isBlank(data) || StrUtil.isBlank(privateKeyStr)) {
            throw new IllegalArgumentException("数据和私钥不能为空");
        }
        
        try {
            PrivateKey privateKey = getPrivateKeyFromString(privateKeyStr);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = cipher.doFinal(dataBytes);
            
            return Base64.encode(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("私钥加密失败", e);
        }
    }
    
    /**
     * 使用公钥解密（后端使用）
     * 
     * @param encryptedData 加密的Base64字符串
     * @param publicKeyStr 公钥Base64字符串
     * @return 解密后的原始数据
     */
    public static String decryptWithPublicKey(String encryptedData, String publicKeyStr) {
        if (StrUtil.isBlank(encryptedData) || StrUtil.isBlank(publicKeyStr)) {
            throw new IllegalArgumentException("加密数据和公钥不能为空");
        }
        
        try {
            PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, publicKey);
            
            byte[] encryptedBytes = Base64.decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("公钥解密失败", e);
        }
    }
    
    /**
     * 使用公钥加密（标准用法）
     * 
     * @param data 待加密数据
     * @param publicKeyStr 公钥Base64字符串
     * @return 加密后的Base64字符串
     */
    public static String encryptWithPublicKey(String data, String publicKeyStr) {
        if (StrUtil.isBlank(data) || StrUtil.isBlank(publicKeyStr)) {
            throw new IllegalArgumentException("数据和公钥不能为空");
        }
        
        try {
            PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedBytes = cipher.doFinal(dataBytes);
            
            return Base64.encode(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("公钥加密失败", e);
        }
    }
    
    /**
     * 使用私钥解密（标准用法）
     * 
     * @param encryptedData 加密的Base64字符串
     * @param privateKeyStr 私钥Base64字符串
     * @return 解密后的原始数据
     */
    public static String decryptWithPrivateKey(String encryptedData, String privateKeyStr) {
        if (StrUtil.isBlank(encryptedData) || StrUtil.isBlank(privateKeyStr)) {
            throw new IllegalArgumentException("加密数据和私钥不能为空");
        }
        
        try {
            PrivateKey privateKey = getPrivateKeyFromString(privateKeyStr);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            
            byte[] encryptedBytes = Base64.decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("私钥解密失败", e);
        }
    }
    
    // ==================== RSA数字签名功能 ====================
    
    /**
     * 使用私钥对数据进行数字签名
     * 
     * @param data 待签名数据
     * @param privateKeyStr 私钥Base64字符串
     * @param algorithm 签名算法（如：SHA256withRSA）
     * @return 签名Base64字符串
     */
    public static String signWithPrivateKey(String data, String privateKeyStr, String algorithm) {
        if (StrUtil.isBlank(data) || StrUtil.isBlank(privateKeyStr)) {
            throw new IllegalArgumentException("数据和私钥不能为空");
        }
        
        try {
            PrivateKey privateKey = getPrivateKeyFromString(privateKeyStr);
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            
            byte[] signBytes = signature.sign();
            return Base64.encode(signBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA数字签名失败", e);
        }
    }
    
    /**
     * 使用公钥验证数字签名
     * 
     * @param data 原始数据
     * @param signatureStr 签名Base64字符串
     * @param publicKeyStr 公钥Base64字符串
     * @param algorithm 签名算法（如：SHA256withRSA）
     * @return 验证结果
     */
    public static boolean verifySignatureWithPublicKey(String data, String signatureStr, String publicKeyStr, String algorithm) {
        if (StrUtil.isBlank(data) || StrUtil.isBlank(signatureStr) || StrUtil.isBlank(publicKeyStr)) {
            throw new IllegalArgumentException("数据、签名和公钥不能为空");
        }
        
        try {
            PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            
            byte[] signBytes = Base64.decode(signatureStr);
            return signature.verify(signBytes);
        } catch (Exception e) {
            System.err.println("RSA数字签名验证失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 使用公钥对数据进行数字签名（非标准用法，用于前端公钥签名场景）
     * 
     * @param data 待签名数据
     * @param publicKeyStr 公钥Base64字符串
     * @param algorithm 签名算法
     * @return 签名Base64字符串
     */
    public static String signWithPublicKey(String data, String publicKeyStr, String algorithm) {
        // 注意：这是非标准用法，正常情况下应该使用私钥签名
        // 这里为了支持特殊场景（如前端使用公钥签名）而提供
        if (StrUtil.isBlank(data) || StrUtil.isBlank(publicKeyStr)) {
            throw new IllegalArgumentException("数据和公钥不能为空");
        }
        
        try {
            // 由于Java标准API不支持公钥签名，这里使用加密模拟
            return encryptWithPublicKey(data, publicKeyStr);
        } catch (Exception e) {
            throw new RuntimeException("公钥签名失败", e);
        }
    }
    
    /**
     * 使用私钥验证公钥签名（非标准用法）
     * 
     * @param data 原始数据
     * @param signatureStr 签名Base64字符串
     * @param privateKeyStr 私钥Base64字符串
     * @return 验证结果
     */
    public static boolean verifySignatureWithPrivateKey(String data, String signatureStr, String privateKeyStr) {
        try {
            String decryptedData = decryptWithPrivateKey(signatureStr, privateKeyStr);
            return data.equals(decryptedData);
        } catch (Exception e) {
            System.err.println("私钥验证签名失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 密钥对信息
     */
    public static class KeyPairInfo {
        private final String publicKey;
        private final String privateKey;
        
        public KeyPairInfo(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
        
        public String getPublicKey() {
            return publicKey;
        }
        
        public String getPrivateKey() {
            return privateKey;
        }
        
        @Override
        public String toString() {
            return "KeyPairInfo{" +
                    "publicKey='" + publicKey + '\'' +
                    ", privateKey='" + privateKey + '\'' +
                    '}';
        }
    }
    
    /**
     * 生成密钥对信息
     * 
     * @return 密钥对信息
     */
    public static KeyPairInfo generateKeyPairInfo() {
        KeyPair keyPair = generateKeyPair();
        String publicKey = getPublicKeyString(keyPair);
        String privateKey = getPrivateKeyString(keyPair);
        return new KeyPairInfo(publicKey, privateKey);
    }
}