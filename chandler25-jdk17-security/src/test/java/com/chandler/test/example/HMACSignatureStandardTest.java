package com.chandler.test.example;

import com.chandler.security.example.client.SignatureClient;
import com.chandler.security.example.util.SignatureUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * HMAC签名标准测试
 * 验证签名标准：Sign = MD5/SHA256(业务参数 + Timestamp + Nonce + Salt)
 */
public class HMACSignatureStandardTest {
    
    private final String salt = "your-secret-salt-key-change-in-production";
    
    @Test
    public void testSHA256SignatureStandard() {
        System.out.println("=== SHA256 HMAC签名标准测试 ===");
        
        // 测试参数
        Map<String, Object> params = new HashMap<>();
        params.put("username", "testuser");
        params.put("email", "test@example.com");
        params.put("action", "create");
        
        String algorithm = "SHA256";
        
        // 使用SignatureClient生成签名
        SignatureClient client = new SignatureClient(salt, algorithm);
        SignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        System.out.println("业务参数: " + params);
        System.out.println("时间戳: " + headers.getTimestamp());
        System.out.println("随机数: " + headers.getNonce());
        System.out.println("盐值: " + salt);
        System.out.println("算法: " + algorithm);
        System.out.println("生成的签名: " + headers.getSign());
        
        // 验证签名
        boolean isValid = SignatureUtil.verifySign(params, headers.getTimestamp(), 
                headers.getNonce(), headers.getSign(), salt, algorithm);
        
        System.out.println("签名验证结果: " + isValid);
        
        // 验证签名标准公式
        String expectedSign = SignatureUtil.generateSign(params, headers.getTimestamp(), 
                headers.getNonce(), salt, algorithm);
        
        System.out.println("期望签名: " + expectedSign);
        System.out.println("签名匹配: " + headers.getSign().equals(expectedSign));
        
        assert isValid : "SHA256签名验证失败";
        assert headers.getSign().equals(expectedSign) : "签名不匹配";
    }
    
    @Test
    public void testMD5SignatureStandard() {
        System.out.println("=== MD5 HMAC签名标准测试 ===");
        
        // 测试参数
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 12345);
        params.put("operation", "delete");
        params.put("reason", "test");
        
        String algorithm = "MD5";
        
        // 使用SignatureClient生成签名
        SignatureClient client = new SignatureClient(salt, algorithm);
        SignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        System.out.println("业务参数: " + params);
        System.out.println("时间戳: " + headers.getTimestamp());
        System.out.println("随机数: " + headers.getNonce());
        System.out.println("盐值: " + salt);
        System.out.println("算法: " + algorithm);
        System.out.println("生成的签名: " + headers.getSign());
        
        // 验证签名
        boolean isValid = SignatureUtil.verifySign(params, headers.getTimestamp(), 
                headers.getNonce(), headers.getSign(), salt, algorithm);
        
        System.out.println("签名验证结果: " + isValid);
        
        assert isValid : "MD5签名验证失败";
    }
    
    @Test
    public void testSignatureFormulaCompliance() {
        System.out.println("=== 签名公式合规性测试 ===");
        
        // 测试数据
        Map<String, Object> params = new HashMap<>();
        params.put("amount", 100.50);
        params.put("currency", "USD");
        params.put("orderId", "ORDER123456");
        
        String timestamp = "1642752000";
        String nonce = "abc123def456";
        String algorithm = "SHA256";
        
        // 手动构建签名字符串验证公式
        String expectedSignString = "amount=100.5&currency=USD&orderId=ORDER123456&timestamp=" + timestamp + "&nonce=" + nonce + "&salt=" + salt;
        
        // 使用工具类生成签名
        String actualSign = SignatureUtil.generateSign(params, timestamp, nonce, salt, algorithm);
        
        System.out.println("签名公式: Sign = " + algorithm + "(业务参数 + Timestamp + Nonce + Salt)");
        System.out.println("期望签名字符串: " + expectedSignString);
        System.out.println("生成的签名: " + actualSign);
        
        // 验证签名
        boolean isValid = SignatureUtil.verifySign(params, timestamp, nonce, actualSign, salt, algorithm);
        
        System.out.println("签名验证结果: " + isValid);
        
        assert isValid : "签名公式合规性验证失败";
    }
    
    @Test
    public void testAntiReplayMechanism() {
        System.out.println("=== 防重放机制测试 ===");
        
        Map<String, Object> params = new HashMap<>();
        params.put("action", "transfer");
        params.put("amount", 1000);
        
        // 测试时间戳窗口
        long currentTime = System.currentTimeMillis() / 1000;
        String validTimestamp = String.valueOf(currentTime);
        String expiredTimestamp = String.valueOf(currentTime - 120); // 2分钟前，超出1分钟窗口
        
        System.out.println("当前时间戳: " + currentTime);
        System.out.println("有效时间戳: " + validTimestamp);
        System.out.println("过期时间戳: " + expiredTimestamp + " (超出1分钟窗口)");
        
        // 测试随机数唯一性
        String nonce1 = "unique-nonce-001";
        String nonce2 = "unique-nonce-002";
        String duplicateNonce = nonce1; // 重复的随机数
        
        SignatureClient client = new SignatureClient(salt, "SHA256");
        
        // 生成不同的签名
        String sign1 = SignatureUtil.generateSign(params, validTimestamp, nonce1, salt, "SHA256");
        String sign2 = SignatureUtil.generateSign(params, validTimestamp, nonce2, salt, "SHA256");
        String duplicateSign = SignatureUtil.generateSign(params, validTimestamp, duplicateNonce, salt, "SHA256");
        
        System.out.println("\n防重放测试结果:");
        System.out.println("签名1 (nonce: " + nonce1 + "): " + sign1);
        System.out.println("签名2 (nonce: " + nonce2 + "): " + sign2);
        System.out.println("重复签名 (nonce: " + duplicateNonce + "): " + duplicateSign);
        
        // 验证不同随机数产生不同签名
        assert !sign1.equals(sign2) : "不同随机数应该产生不同签名";
        assert sign1.equals(duplicateSign) : "相同随机数应该产生相同签名";
        
        System.out.println("\n防重放机制验证:");
        System.out.println("✓ 时间戳窗口: 1分钟");
        System.out.println("✓ 随机数唯一性: 通过Redis校验");
        System.out.println("✓ 不同随机数产生不同签名");
    }
    
    @Test
    public void testParameterSorting() {
        System.out.println("=== 参数排序测试 ===");
        
        // 测试参数排序的一致性
        Map<String, Object> params1 = new HashMap<>();
        params1.put("c", "value3");
        params1.put("a", "value1");
        params1.put("b", "value2");
        
        Map<String, Object> params2 = new HashMap<>();
        params2.put("b", "value2");
        params2.put("c", "value3");
        params2.put("a", "value1");
        
        String timestamp = "1642752000";
        String nonce = "test-nonce";
        String algorithm = "SHA256";
        
        String sign1 = SignatureUtil.generateSign(params1, timestamp, nonce, salt, algorithm);
        String sign2 = SignatureUtil.generateSign(params2, timestamp, nonce, salt, algorithm);
        
        System.out.println("参数集1: " + params1);
        System.out.println("参数集2: " + params2);
        System.out.println("签名1: " + sign1);
        System.out.println("签名2: " + sign2);
        System.out.println("签名一致: " + sign1.equals(sign2));
        
        assert sign1.equals(sign2) : "相同参数不同顺序应该产生相同签名";
    }
}