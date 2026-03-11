package com.chandler.security.example.client;

import cn.hutool.core.util.IdUtil;
import com.chandler.security.example.util.SignatureUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * RSA签名客户端工具类
 * 支持标准RSA数字签名和前端私钥加密模式
 */
public class RSASignatureClient {
    
    private final String privateKey;
    private final String rsaAlgorithm;
    private final boolean usePrivateKeyEncryption;
    
    /**
     * 标准RSA数字签名构造器
     * 
     * @param privateKey 私钥Base64字符串
     * @param rsaAlgorithm RSA签名算法（如：SHA256withRSA）
     */
    public RSASignatureClient(String privateKey, String rsaAlgorithm) {
        this.privateKey = privateKey;
        this.rsaAlgorithm = rsaAlgorithm;
        this.usePrivateKeyEncryption = false;
    }
    
    /**
     * 前端私钥加密模式构造器
     * 
     * @param privateKey 私钥Base64字符串
     * @param usePrivateKeyEncryption 是否使用私钥加密模式
     */
    public RSASignatureClient(String privateKey, boolean usePrivateKeyEncryption) {
        this.privateKey = privateKey;
        this.rsaAlgorithm = null;
        this.usePrivateKeyEncryption = usePrivateKeyEncryption;
    }
    
    /**
     * 生成RSA签名请求头
     * 
     * @param params 业务参数
     * @return 签名请求头
     */
    public SignatureHeaders generateHeaders(Map<String, Object> params) {
        // 生成时间戳（秒级）
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        
        // 生成随机数
        String nonce = IdUtil.fastSimpleUUID();
        
        // 生成签名
        String sign;
        if (usePrivateKeyEncryption) {
            // 前端私钥加密模式
            sign = SignatureUtil.generatePrivateKeyEncryptSign(params, timestamp, nonce, privateKey);
            System.out.println("生成私钥加密签名");
        } else {
            // 标准RSA数字签名
            sign = SignatureUtil.generateRSASign(params, timestamp, nonce, privateKey, rsaAlgorithm);
            System.out.println("生成RSA数字签名");
        }
        
        SignatureHeaders headers = new SignatureHeaders();
        headers.setTimestamp(timestamp);
        headers.setNonce(nonce);
        headers.setSign(sign);
        
        System.out.println("生成RSA签名请求头 - timestamp: " + timestamp + ", nonce: " + nonce + ", sign: " + sign);
        
        return headers;
    }
    
    /**
     * 生成签名请求头（无参数）
     * 
     * @return 签名请求头
     */
    public SignatureHeaders generateHeaders() {
        return generateHeaders(new HashMap<>());
    }
    
    /**
     * 签名请求头
     */
    public static class SignatureHeaders {
        private String timestamp;
        private String nonce;
        private String sign;
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
        
        public String getNonce() {
            return nonce;
        }
        
        public void setNonce(String nonce) {
            this.nonce = nonce;
        }
        
        public String getSign() {
            return sign;
        }
        
        public void setSign(String sign) {
            this.sign = sign;
        }
        
        /**
         * 转换为HTTP请求头Map
         * 
         * @return HTTP请求头Map
         */
        public Map<String, String> toHeaderMap() {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Timestamp", timestamp);
            headers.put("X-Nonce", nonce);
            headers.put("X-Sign", sign);
            return headers;
        }
    }
}