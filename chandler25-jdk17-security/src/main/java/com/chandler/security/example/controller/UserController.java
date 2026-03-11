package com.chandler.security.example.controller;

import com.chandler.security.example.annotation.RequireSignature;
import com.chandler.security.example.client.SignatureClient;
import com.chandler.security.example.domain.dataobject.User;
import com.chandler.security.example.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * restful风格的接口
 *
 * @author 钱丁君-chandler 2019/5/17下午2:00
 * @since 1.8
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "示例接口", description = "提供示例内容展示SpringDoc集成效果")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Value("${signature.salt}")
    private String salt;
    
    @Value("${signature.algorithm}")
    private String algorithm;

    @Operation(description = "查询")
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.detail(id);
    }

    @Operation(description = "新增")
    @PostMapping("/create")
    @RequireSignature
    public void add(@RequestBody User user) {
        userService.add(user);
    }

    @Operation(description = "删除")
    @DeleteMapping("/{id}")
    @RequireSignature
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

    @Operation(description = "修改")
    @PostMapping("/modify")
    @RequireSignature
    public void update(@RequestBody User user) {
        userService.update(user);
    }
    
    @Operation(description = "生成HMAC签名示例", summary = "演示如何生成HMAC请求签名（MD5/SHA256）")
    @PostMapping("/signature/demo")
    public Map<String, Object> generateSignatureDemo(@RequestBody Map<String, Object> params) {
        // 使用HMAC签名客户端
        SignatureClient client = new SignatureClient(salt, algorithm);
        SignatureClient.SignatureHeaders headers = client.generateHeaders(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("signatureType", "HMAC签名");
        result.put("algorithm", algorithm);
        result.put("params", params);
        result.put("headers", headers.toHeaderMap());
        result.put("signatureStandard", Map.of(
            "formula", "Sign = " + algorithm + "(业务参数 + Timestamp + Nonce + Salt)",
            "timestampWindow", "1分钟",
            "noncePolicy", "1分钟内同一Nonce只能使用一次"
        ));
        result.put("usage", "将headers中的值设置到HTTP请求头中：X-Timestamp, X-Nonce, X-Sign");
        result.put("example", Map.of(
            "curl", String.format(
                "curl -X POST http://localhost:17680/api/user/create \\\n" +
                "  -H \"Content-Type: application/json\" \\\n" +
                "  -H \"X-Timestamp: %s\" \\\n" +
                "  -H \"X-Nonce: %s\" \\\n" +
                "  -H \"X-Sign: %s\" \\\n" +
                "  -d '%s'",
                headers.getTimestamp(),
                headers.getNonce(),
                headers.getSign(),
                "{\"username\":\"testuser\",\"email\":\"test@example.com\"}"
            )
        ));
        
        return result;
    }
}
