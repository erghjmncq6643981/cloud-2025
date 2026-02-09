package com.chandler.test.example;

import com.chandler.security.example.client.RSASignatureClient;
import com.chandler.security.example.util.RSAUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * UserController RSA签名功能测试
 */
public class UserControllerRSATest {
    
    @Test
    public void testRSASignatureGeneration() {
        System.out.println("=== UserController RSA签名生成测试 ===");
        
        // 生成密钥对
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        String privateKey = keyPairInfo.getPrivateKey();
        String publicKey = keyPairInfo.getPublicKey();
        
        System.out.println("生成的密钥对:");
        System.out.println("公钥: " + publicKey.substring(0, 50) + "...");
        System.out.println("私钥: " + privateKey.substring(0, 50) + "...");
        
        // 模拟UserController的签名生成逻辑
        Map<String, Object> params = new HashMap<>();
        params.put("username", "testuser");
        params.put("email", "test@example.com");
        params.put("action", "create");
        
        // 使用标准RSA数字签名
        RSASignatureClient client = new RSASignatureClient(privateKey, "SHA256withRSA");
        RSASignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        System.out.println("\n生成的RSA签名请求头:");
        System.out.println("X-Timestamp: " + headers.getTimestamp());
        System.out.println("X-Nonce: " + headers.getNonce());
        System.out.println("X-Sign: " + headers.getSign().substring(0, 50) + "...");
        
        // 验证签名
        boolean isValid = RSAUtil.verifySignatureWithPublicKey(
            buildSignString(params, headers.getTimestamp(), headers.getNonce()),
            headers.getSign(),
            publicKey,
            "SHA256withRSA"
        );
        
        System.out.println("\nRSA签名验证结果: " + isValid);
        
        // 生成curl示例
        String curlExample = String.format(
            "curl -X POST http://localhost:17680/api/user/create \\\n" +
            "  -H \"Content-Type: application/json\" \\\n" +
            "  -H \"X-Timestamp: %s\" \\\n" +
            "  -H \"X-Nonce: %s\" \\\n" +
            "  -H \"X-Sign: %s\" \\\n" +
            "  -d '{\"username\":\"testuser\",\"email\":\"test@example.com\"}'",
            headers.getTimestamp(),
            headers.getNonce(),
            headers.getSign()
        );
        
        System.out.println("\ncurl使用示例:");
        System.out.println(curlExample);
        
        assert isValid : "RSA签名验证失败";
    }
    
    @Test
    public void testPrivateKeyEncryptionMode() {
        System.out.println("=== 前端私钥加密模式测试 ===");
        
        // 生成密钥对
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        String privateKey = keyPairInfo.getPrivateKey();
        String publicKey = keyPairInfo.getPublicKey();
        
        // 测试参数
        Map<String, Object> params = new HashMap<>();
        params.put("username", "testuser");
        params.put("action", "modify");
        
        // 使用私钥加密模式
        RSASignatureClient client = new RSASignatureClient(privateKey, true);
        RSASignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        System.out.println("私钥加密签名请求头:");
        System.out.println("X-Timestamp: " + headers.getTimestamp());
        System.out.println("X-Nonce: " + headers.getNonce());
        System.out.println("X-Sign: " + headers.getSign().substring(0, 50) + "...");
        
        // 验证私钥加密签名（公钥解密）
        try {
            String signString = buildSignString(params, headers.getTimestamp(), headers.getNonce());
            String decryptedSignString = RSAUtil.decryptWithPublicKey(headers.getSign(), publicKey);
            boolean isValid = signString.equals(decryptedSignString);
            
            System.out.println("原始签名字符串: " + signString);
            System.out.println("解密后字符串: " + decryptedSignString);
            System.out.println("私钥加密签名验证结果: " + isValid);
            
            assert isValid : "私钥加密签名验证失败";
        } catch (Exception e) {
            System.err.println("私钥加密签名验证异常: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 构建签名字符串（与SignatureUtil中的逻辑保持一致）
     */
    private String buildSignString(Map<String, Object> params, String timestamp, String nonce) {
        StringBuilder signStr = new StringBuilder();
        
        // 参数排序并拼接
        params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (entry.getValue() != null && !entry.getValue().toString().trim().isEmpty()) {
                    signStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
            });
        
        // 添加时间戳和随机数
        signStr.append("timestamp=").append(timestamp).append("&");
        signStr.append("nonce=").append(nonce);
        
        return signStr.toString();
    }
}