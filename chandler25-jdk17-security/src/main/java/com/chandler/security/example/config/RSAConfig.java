package com.chandler.security.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RSA配置类
 */
@Component
@ConfigurationProperties(prefix = "rsa")
public class RSAConfig {
    
    /**
     * 是否启用RSA签名
     */
    private boolean enabled = false;
    
    /**
     * 公钥（用于验证签名和解密）
     */
    private String publicKey;
    
    /**
     * 私钥（用于生成签名和加密）
     */
    private String privateKey;
    
    /**
     * RSA签名算法
     */
    private String signatureAlgorithm = "SHA256withRSA";
    
    /**
     * 是否使用私钥加密模式（前端私钥加密，后端公钥解密）
     */
    private boolean usePrivateKeyEncryption = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public boolean isUsePrivateKeyEncryption() {
        return usePrivateKeyEncryption;
    }

    public void setUsePrivateKeyEncryption(boolean usePrivateKeyEncryption) {
        this.usePrivateKeyEncryption = usePrivateKeyEncryption;
    }
}