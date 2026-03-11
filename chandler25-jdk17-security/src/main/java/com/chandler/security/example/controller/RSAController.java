package com.chandler.security.example.controller;

import com.chandler.security.example.client.RSASignatureClient;
import com.chandler.security.example.util.RSAUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RSA加解密和签名控制器
 */
@Tag(name = "RSA加解密", description = "RSA加解密和数字签名相关接口")
@RestController
@RequestMapping("/api/rsa")
public class RSAController {
    
    @Operation(summary = "生成RSA密钥对", description = "生成新的RSA公私钥对")
    @PostMapping("/generate-keypair")
    public Map<String, Object> generateKeyPair() {
        RSAUtil.KeyPairInfo keyPairInfo = RSAUtil.generateKeyPairInfo();
        
        Map<String, Object> result = new HashMap<>();
        result.put("publicKey", keyPairInfo.getPublicKey());
        result.put("privateKey", keyPairInfo.getPrivateKey());
        result.put("usage", Map.of(
            "publicKey", "用于后端验证签名和解密",
            "privateKey", "用于前端生成签名和加密"
        ));
        
        return result;
    }
    
    @Operation(summary = "私钥加密", description = "使用私钥加密数据（前端使用）")
    @PostMapping("/encrypt-with-private-key")
    public Map<String, Object> encryptWithPrivateKey(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String privateKey = request.get("privateKey");
        
        String encryptedData = RSAUtil.encryptWithPrivateKey(data, privateKey);
        
        Map<String, Object> result = new HashMap<>();
        result.put("encryptedData", encryptedData);
        result.put("originalData", data);
        
        return result;
    }
    
    @Operation(summary = "公钥解密", description = "使用公钥解密数据（后端使用）")
    @PostMapping("/decrypt-with-public-key")
    public Map<String, Object> decryptWithPublicKey(@RequestBody Map<String, String> request) {
        String encryptedData = request.get("encryptedData");
        String publicKey = request.get("publicKey");
        
        String decryptedData = RSAUtil.decryptWithPublicKey(encryptedData, publicKey);
        
        Map<String, Object> result = new HashMap<>();
        result.put("decryptedData", decryptedData);
        result.put("encryptedData", encryptedData);
        
        return result;
    }
    
    @Operation(summary = "生成RSA数字签名", description = "使用私钥生成数字签名")
    @PostMapping("/generate-signature")
    public Map<String, Object> generateSignature(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String privateKey = request.get("privateKey");
        String algorithm = request.getOrDefault("algorithm", "SHA256withRSA");
        
        String signature = RSAUtil.signWithPrivateKey(data, privateKey, algorithm);
        
        Map<String, Object> result = new HashMap<>();
        result.put("signature", signature);
        result.put("data", data);
        result.put("algorithm", algorithm);
        
        return result;
    }
    
    @Operation(summary = "验证RSA数字签名", description = "使用公钥验证数字签名")
    @PostMapping("/verify-signature")
    public Map<String, Object> verifySignature(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String signature = request.get("signature");
        String publicKey = request.get("publicKey");
        String algorithm = request.getOrDefault("algorithm", "SHA256withRSA");
        
        boolean isValid = RSAUtil.verifySignatureWithPublicKey(data, signature, publicKey, algorithm);
        
        Map<String, Object> result = new HashMap<>();
        result.put("isValid", isValid);
        result.put("data", data);
        result.put("signature", signature);
        result.put("algorithm", algorithm);
        
        return result;
    }
    
    @Operation(summary = "生成RSA接口签名示例", description = "演示如何生成RSA接口签名")
    @PostMapping("/signature-demo")
    public Map<String, Object> generateRSASignatureDemo(@RequestBody Map<String, Object> request) {
        String privateKey = (String) request.get("privateKey");
        String algorithm = (String) request.getOrDefault("algorithm", "SHA256withRSA");
        Boolean usePrivateKeyEncryption = (Boolean) request.getOrDefault("usePrivateKeyEncryption", false);
        
        // 移除非业务参数
        Map<String, Object> params = new HashMap<>(request);
        params.remove("privateKey");
        params.remove("algorithm");
        params.remove("usePrivateKeyEncryption");
        
        // 生成签名
        RSASignatureClient client;
        if (usePrivateKeyEncryption) {
            client = new RSASignatureClient(privateKey, true);
        } else {
            client = new RSASignatureClient(privateKey, algorithm);
        }
        
        RSASignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("params", params);
        result.put("headers", headers.toHeaderMap());
        result.put("signatureType", usePrivateKeyEncryption ? "私钥加密模式" : "标准RSA数字签名");
        result.put("usage", "将headers中的值设置到HTTP请求头中：X-Timestamp, X-Nonce, X-Sign");
        
        return result;
    }
}