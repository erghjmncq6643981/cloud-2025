package com.chandler.security.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 签名验证配置
 */
@Component
@ConfigurationProperties(prefix = "signature")
public class SignatureConfig {
    
    /**
     * 签名算法类型：MD5 或 SHA256
     */
    private String algorithm = "SHA256";
    
    /**
     * 时间戳校验窗口（秒）
     */
    private long timestampWindow = 60;
    
    /**
     * 随机数过期时间（秒）
     */
    private long nonceExpireTime = 60;
    
    /**
     * 签名盐值
     */
    private String salt = "your-secret-salt-key";
    
    /**
     * 是否启用签名验证
     */
    private boolean enabled = true;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public long getTimestampWindow() {
        return timestampWindow;
    }

    public void setTimestampWindow(long timestampWindow) {
        this.timestampWindow = timestampWindow;
    }

    public long getNonceExpireTime() {
        return nonceExpireTime;
    }

    public void setNonceExpireTime(long nonceExpireTime) {
        this.nonceExpireTime = nonceExpireTime;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}