package com.chandler.security.example.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;

import java.util.Map;
import java.util.TreeMap;

/**
 * 签名工具类
 * 支持HMAC和RSA两种签名方式
 */
public class SignatureUtil {
    
    /**
     * 生成HMAC签名
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param salt 盐值
     * @param algorithm 算法类型（MD5/SHA256）
     * @return 签名字符串
     */
    public static String generateSign(Map<String, Object> params, String timestamp, String nonce, String salt, String algorithm) {
        // 1. 参数排序
        TreeMap<String, Object> sortedParams = new TreeMap<>(params);
        
        // 2. 构建签名字符串
        StringBuilder signStr = new StringBuilder();
        
        // 添加业务参数
        for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            if (entry.getValue() != null && StrUtil.isNotBlank(entry.getValue().toString())) {
                signStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }
        
        // 添加时间戳和随机数
        signStr.append("timestamp=").append(timestamp).append("&");
        signStr.append("nonce=").append(nonce).append("&");
        signStr.append("salt=").append(salt);
        
        String signString = signStr.toString();
        System.out.println("签名原始字符串: " + signString);
        
        // 3. 计算签名
        String signature;
        if ("MD5".equalsIgnoreCase(algorithm)) {
            signature = DigestUtil.md5Hex(signString);
        } else {
            signature = DigestUtil.sha256Hex(signString);
        }
        
        System.out.println("生成的HMAC签名: " + signature);
        return signature;
    }
    
    /**
     * 生成RSA签名
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param privateKeyStr 私钥字符串
     * @param algorithm RSA签名算法（如：SHA256withRSA）
     * @return RSA签名字符串
     */
    public static String generateRSASign(Map<String, Object> params, String timestamp, String nonce, String privateKeyStr, String algorithm) {
        // 1. 构建待签名字符串（不包含salt）
        String signString = buildSignString(params, timestamp, nonce);
        
        System.out.println("RSA签名原始字符串: " + signString);
        
        // 2. 使用RSA私钥签名
        String signature = RSAUtil.signWithPrivateKey(signString, privateKeyStr, algorithm);
        
        System.out.println("生成的RSA签名: " + signature);
        return signature;
    }
    
    /**
     * 生成前端私钥加密签名（特殊场景）
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param privateKeyStr 前端私钥字符串
     * @return 加密签名字符串
     */
    public static String generatePrivateKeyEncryptSign(Map<String, Object> params, String timestamp, String nonce, String privateKeyStr) {
        // 1. 构建待签名字符串
        String signString = buildSignString(params, timestamp, nonce);
        
        System.out.println("私钥加密签名原始字符串: " + signString);
        
        // 2. 使用私钥加密
        String signature = RSAUtil.encryptWithPrivateKey(signString, privateKeyStr);
        
        System.out.println("生成的私钥加密签名: " + signature);
        return signature;
    }
    
    /**
     * 构建签名字符串
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @return 签名字符串
     */
    private static String buildSignString(Map<String, Object> params, String timestamp, String nonce) {
        TreeMap<String, Object> sortedParams = new TreeMap<>(params);
        StringBuilder signStr = new StringBuilder();
        
        // 添加业务参数
        for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            if (entry.getValue() != null && StrUtil.isNotBlank(entry.getValue().toString())) {
                signStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }
        
        // 添加时间戳和随机数
        signStr.append("timestamp=").append(timestamp).append("&");
        signStr.append("nonce=").append(nonce);
        
        return signStr.toString();
    }
    
    /**
     * 验证HMAC签名
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param sign 客户端签名
     * @param salt 盐值
     * @param algorithm 算法类型
     * @return 验证结果
     */
    public static boolean verifySign(Map<String, Object> params, String timestamp, String nonce, String sign, String salt, String algorithm) {
        String serverSign = generateSign(params, timestamp, nonce, salt, algorithm);
        return serverSign.equals(sign);
    }
    
    /**
     * 验证RSA签名
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param sign 客户端RSA签名
     * @param publicKeyStr 公钥字符串
     * @param algorithm RSA签名算法
     * @return 验证结果
     */
    public static boolean verifyRSASign(Map<String, Object> params, String timestamp, String nonce, String sign, String publicKeyStr, String algorithm) {
        String signString = buildSignString(params, timestamp, nonce);
        return RSAUtil.verifySignatureWithPublicKey(signString, sign, publicKeyStr, algorithm);
    }
    
    /**
     * 验证前端私钥加密签名（后端公钥解密）
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param encryptedSign 前端私钥加密的签名
     * @param publicKeyStr 后端公钥字符串
     * @return 验证结果
     */
    public static boolean verifyPrivateKeyEncryptSign(Map<String, Object> params, String timestamp, String nonce, String encryptedSign, String publicKeyStr) {
        try {
            // 1. 构建原始签名字符串
            String originalSignString = buildSignString(params, timestamp, nonce);
            
            // 2. 使用公钥解密
            String decryptedSignString = RSAUtil.decryptWithPublicKey(encryptedSign, publicKeyStr);
            
            // 3. 比较原始字符串和解密后的字符串
            return originalSignString.equals(decryptedSignString);
        } catch (Exception e) {
            System.err.println("验证私钥加密签名失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 从JSON字符串解析参数
     * 
     * @param jsonStr JSON字符串
     * @return 参数Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseParams(String jsonStr) {
        if (StrUtil.isBlank(jsonStr)) {
            return new TreeMap<>();
        }
        try {
            return JSON.parseObject(jsonStr, Map.class);
        } catch (Exception e) {
            System.err.println("解析参数失败: " + jsonStr + ", 错误: " + e.getMessage());
            return new TreeMap<>();
        }
    }
}