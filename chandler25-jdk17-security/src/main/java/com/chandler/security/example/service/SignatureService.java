package com.chandler.security.example.service;

import cn.hutool.core.util.StrUtil;
import com.chandler.security.example.config.RSAConfig;
import com.chandler.security.example.config.SignatureConfig;
import com.chandler.security.example.util.SignatureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 签名验证服务
 * 支持HMAC和RSA两种签名验证方式
 */
@Service
public class SignatureService {
    
    @Autowired
    private SignatureConfig signatureConfig;
    
    @Autowired
    private RSAConfig rsaConfig;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final String NONCE_PREFIX = "signature:nonce:";
    
    /**
     * 验证请求签名（支持HMAC和RSA）
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param sign 签名
     * @return 验证结果
     */
    public SignatureResult verifySignature(Map<String, Object> params, String timestamp, String nonce, String sign) {
        // 1. 检查是否启用签名验证
        if (!signatureConfig.isEnabled() && !rsaConfig.isEnabled()) {
            return SignatureResult.success();
        }
        
        // 2. 参数校验
        if (StrUtil.isBlank(timestamp) || StrUtil.isBlank(nonce) || StrUtil.isBlank(sign)) {
            return SignatureResult.fail("签名参数不完整");
        }
        
        // 3. 时间戳校验
        if (!validateTimestamp(timestamp)) {
            return SignatureResult.fail("时间戳校验失败");
        }
        
        // 4. 随机数校验（防重放）
        if (!validateNonce(nonce)) {
            return SignatureResult.fail("随机数校验失败，可能存在重放攻击");
        }
        
        // 5. 签名校验 - 优先使用RSA签名
        boolean signValid = false;
        if (rsaConfig.isEnabled()) {
            signValid = validateRSASign(params, timestamp, nonce, sign);
            if (!signValid) {
                return SignatureResult.fail("RSA签名校验失败");
            }
        } else if (signatureConfig.isEnabled()) {
            signValid = validateHMACSign(params, timestamp, nonce, sign);
            if (!signValid) {
                return SignatureResult.fail("HMAC签名校验失败");
            }
        }
        
        // 6. 记录随机数到Redis
        recordNonce(nonce);
        
        return SignatureResult.success();
    }
    
    /**
     * 验证RSA签名
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param sign 客户端签名
     * @return 验证结果
     */
    private boolean validateRSASign(Map<String, Object> params, String timestamp, String nonce, String sign) {
        try {
            if (rsaConfig.isUsePrivateKeyEncryption()) {
                // 前端私钥加密，后端公钥解密模式
                return SignatureUtil.verifyPrivateKeyEncryptSign(params, timestamp, nonce, sign, rsaConfig.getPublicKey());
            } else {
                // 标准RSA数字签名模式
                return SignatureUtil.verifyRSASign(params, timestamp, nonce, sign, rsaConfig.getPublicKey(), rsaConfig.getSignatureAlgorithm());
            }
        } catch (Exception e) {
            System.err.println("RSA签名验证异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证HMAC签名
     * 
     * @param params 业务参数
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param sign 客户端签名
     * @return 验证结果
     */
    private boolean validateHMACSign(Map<String, Object> params, String timestamp, String nonce, String sign) {
        return SignatureUtil.verifySign(params, timestamp, nonce, sign, 
                signatureConfig.getSalt(), signatureConfig.getAlgorithm());
    }
    
    /**
     * 验证时间戳
     * 
     * @param timestamp 时间戳字符串
     * @return 验证结果
     */
    private boolean validateTimestamp(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            long timeDiff = Math.abs(currentTime - requestTime);
            
            System.out.println("时间戳校验 - 请求时间: " + requestTime + ", 当前时间: " + currentTime + ", 时间差: " + timeDiff + "秒");
            
            return timeDiff <= signatureConfig.getTimestampWindow();
        } catch (NumberFormatException e) {
            System.err.println("时间戳格式错误: " + timestamp + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证随机数（防重放）
     * 
     * @param nonce 随机数
     * @return 验证结果
     */
    private boolean validateNonce(String nonce) {
        String key = NONCE_PREFIX + nonce;
        String exists = redisTemplate.opsForValue().get(key);
        
        if (StrUtil.isNotBlank(exists)) {
            System.out.println("检测到重放攻击，随机数已存在: " + nonce);
            return false;
        }
        
        return true;
    }
    
    /**
     * 记录随机数到Redis
     * 
     * @param nonce 随机数
     */
    private void recordNonce(String nonce) {
        String key = NONCE_PREFIX + nonce;
        redisTemplate.opsForValue().set(key, "1", signatureConfig.getNonceExpireTime(), TimeUnit.SECONDS);
        System.out.println("随机数已记录到Redis: " + nonce);
    }
    
    /**
     * 签名验证结果
     */
    public static class SignatureResult {
        private final boolean success;
        private final String message;
        
        private SignatureResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static SignatureResult success() {
            return new SignatureResult(true, "验证成功");
        }
        
        public static SignatureResult fail(String message) {
            return new SignatureResult(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
}