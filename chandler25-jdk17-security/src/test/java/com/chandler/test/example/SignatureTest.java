package com.chandler.test.example;

import com.chandler.security.example.client.SignatureClient;
import com.chandler.security.example.util.SignatureUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 签名功能测试
 */
public class SignatureTest {
    
    private final String salt = "your-secret-salt-key-change-in-production";
    private final String algorithm = "SHA256";
    
    @Test
    public void testSignatureGeneration() {
        // 测试参数
        Map<String, Object> params = new HashMap<>();
        params.put("username", "testuser");
        params.put("email", "test@example.com");
        params.put("age", 25);
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce-12345";
        
        // 生成签名
        String signature = SignatureUtil.generateSign(params, timestamp, nonce, salt, algorithm);
        
        System.out.println("=== 签名测试 ===");
        System.out.println("参数: " + params);
        System.out.println("时间戳: " + timestamp);
        System.out.println("随机数: " + nonce);
        System.out.println("签名: " + signature);
        
        // 验证签名
        boolean isValid = SignatureUtil.verifySign(params, timestamp, nonce, signature, salt, algorithm);
        System.out.println("签名验证结果: " + isValid);
        
        assert isValid : "签名验证失败";
    }
    
    @Test
    public void testSignatureClient() {
        SignatureClient client = new SignatureClient(salt, algorithm);
        
        Map<String, Object> params = new HashMap<>();
        params.put("action", "create_user");
        params.put("userId", 123);
        
        SignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        System.out.println("=== 客户端签名测试 ===");
        System.out.println("请求头: " + headers.toHeaderMap());
        
        // 验证生成的签名
        boolean isValid = SignatureUtil.verifySign(params, headers.getTimestamp(), 
                headers.getNonce(), headers.getSign(), salt, algorithm);
        
        System.out.println("客户端签名验证结果: " + isValid);
        
        assert isValid : "客户端签名验证失败";
    }
}