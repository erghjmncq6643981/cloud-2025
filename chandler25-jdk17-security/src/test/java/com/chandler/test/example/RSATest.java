package com.chandler.test.example;

import com.chandler.security.example.client.RSASignatureClient;
import com.chandler.security.example.util.RSAUtil;
import com.chandler.security.example.util.SignatureUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * RSA功能测试
 */
public class RSATest {
    
    @Test
    public void testRSAKeyGeneration() {
        System.out.println("=== RSA密钥生成测试 ===");
        
        // 生成密钥对
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        
        System.out.println("公钥: " + keyPairInfo.getPublicKey());
        System.out.println("私钥: " + keyPairInfo.getPrivateKey());
        
        assert keyPairInfo.getPublicKey() != null : "公钥生成失败";
        assert keyPairInfo.getPrivateKey() != null : "私钥生成失败";
    }
    
    @Test
    public void testPrivateKeyEncryptPublicKeyDecrypt() {
        System.out.println("=== 前端私钥加密，后端公钥解密测试 ===");
        
        // 生成密钥对
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        String publicKey = keyPairInfo.getPublicKey();
        String privateKey = keyPairInfo.getPrivateKey();
        
        // 测试数据
        String originalData = "Hello RSA Encryption!";
        
        // 前端私钥加密
        String encryptedData = RSAUtil.encryptWithPrivateKey(originalData, privateKey);
        System.out.println("原始数据: " + originalData);
        System.out.println("加密数据: " + encryptedData);
        
        // 后端公钥解密
        String decryptedData = RSAUtil.decryptWithPublicKey(encryptedData, publicKey);
        System.out.println("解密数据: " + decryptedData);
        
        assert originalData.equals(decryptedData) : "加解密失败";
    }
    
    @Test
    public void testRSADigitalSignature() {
        System.out.println("=== RSA数字签名测试 ===");
        
        // 生成密钥对
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        String publicKey = keyPairInfo.getPublicKey();
        String privateKey = keyPairInfo.getPrivateKey();
        
        // 测试数据
        String data = "This is a test message for RSA signature";
        String algorithm = "SHA256withRSA";
        
        // 私钥签名
        String signature = RSAUtil.signWithPrivateKey(data, privateKey, algorithm);
        System.out.println("原始数据: " + data);
        System.out.println("数字签名: " + signature);
        
        // 公钥验证
        boolean isValid = RSAUtil.verifySignatureWithPublicKey(data, signature, publicKey, algorithm);
        System.out.println("签名验证结果: " + isValid);
        
        assert isValid : "RSA数字签名验证失败";
    }
    
    @Test
    public void testRSASignatureClient() {
        System.out.println("=== RSA签名客户端测试 ===");
        
        // 生成密钥对
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        String publicKey = keyPairInfo.getPublicKey();
        String privateKey = keyPairInfo.getPrivateKey();
        
        // 测试参数
        Map<String, Object> params = new HashMap<>();
        params.put("username", "testuser");
        params.put("action", "create");
        params.put("userId", 123);
        
        // 测试标准RSA数字签名
        RSASignatureClient client1 = new RSASignatureClient(privateKey, "SHA256withRSA");
        RSASignatureClient.SignatureHeaders headers1 = client1.generateHeaders(params);
        
        System.out.println("标准RSA签名请求头: " + headers1.toHeaderMap());
        
        // 验证标准RSA签名
        boolean isValid1 = SignatureUtil.verifyRSASign(params, headers1.getTimestamp(), 
                headers1.getNonce(), headers1.getSign(), publicKey, "SHA256withRSA");
        System.out.println("标准RSA签名验证结果: " + isValid1);
        
        // 测试私钥加密模式
        RSASignatureClient client2 = new RSASignatureClient(privateKey, true);
        RSASignatureClient.SignatureHeaders headers2 = client2.generateHeaders(params);
        
        System.out.println("私钥加密签名请求头: " + headers2.toHeaderMap());
        
        // 验证私钥加密签名
        boolean isValid2 = SignatureUtil.verifyPrivateKeyEncryptSign(params, headers2.getTimestamp(), 
                headers2.getNonce(), headers2.getSign(), publicKey);
        System.out.println("私钥加密签名验证结果: " + isValid2);
        
        assert isValid1 : "标准RSA签名验证失败";
        assert isValid2 : "私钥加密签名验证失败";
    }
}