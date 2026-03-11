package com.chandler.security.example.client;

import cn.hutool.core.util.IdUtil;
import com.chandler.security.example.util.SignatureUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端签名工具类
 */
public class SignatureClient {
    
    private final String salt;
    private final String algorithm;
    
    public SignatureClient(String salt, String algorithm) {
        this.salt = salt;
        this.algorithm = algorithm;
    }
    
    /**
     * 生成签名请求头
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
        String sign = SignatureUtil.generateSign(params, timestamp, nonce, salt, algorithm);
        
        SignatureHeaders headers = new SignatureHeaders();
        headers.setTimestamp(timestamp);
        headers.setNonce(nonce);
        headers.setSign(sign);
        
        System.out.println("生成签名请求头 - timestamp: " + timestamp + ", nonce: " + nonce + ", sign: " + sign);
        
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