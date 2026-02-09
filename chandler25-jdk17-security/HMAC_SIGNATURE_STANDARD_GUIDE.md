# 全链路接口签名机制标准指南

## 🎯 签名标准

### 核心算法
```
Sign = MD5/SHA256(业务参数 + Timestamp + Nonce + Salt)
```

### 防重放闭环
- **Timestamp（时间戳）**：服务端校验窗口设为1分钟，超时即拒
- **Nonce（随机数）**：结合Redis校验，1分钟内同一Nonce只能使用一次，彻底杜绝抓包重放

## 🔐 签名机制详解

### 1. 签名生成流程

#### 步骤1：参数排序
```java
// 将所有业务参数按key进行字典序排序
TreeMap<String, Object> sortedParams = new TreeMap<>(params);
```

#### 步骤2：构建签名字符串
```java
// 格式：key1=value1&key2=value2&timestamp=xxx&nonce=xxx&salt=xxx
StringBuilder signStr = new StringBuilder();

// 添加业务参数
for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
    if (entry.getValue() != null && StrUtil.isNotBlank(entry.getValue().toString())) {
        signStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
    }
}

// 添加时间戳和随机数
signStr.append("timestamp=").append(timestamp).append("&");
signStr.append("nonce=").append(nonce).append("&");
signStr.append("salt=").append(salt);
```

#### 步骤3：计算签名
```java
String signature;
if ("MD5".equalsIgnoreCase(algorithm)) {
    signature = DigestUtil.md5Hex(signString);
} else {
    signature = DigestUtil.sha256Hex(signString);
}
```

### 2. 防重放机制

#### 时间戳校验
```java
long requestTime = Long.parseLong(timestamp);
long currentTime = System.currentTimeMillis() / 1000;
long timeDiff = Math.abs(currentTime - requestTime);

// 1分钟窗口校验
return timeDiff <= 60;
```

#### 随机数校验
```java
String key = "signature:nonce:" + nonce;
String exists = redisTemplate.opsForValue().get(key);

if (StrUtil.isNotBlank(exists)) {
    // 检测到重放攻击
    return false;
}

// 记录随机数，1分钟过期
redisTemplate.opsForValue().set(key, "1", 60, TimeUnit.SECONDS);
```

## 🚀 使用示例

### 1. 配置签名参数

```yaml
# application.yaml
signature:
  enabled: true
  algorithm: SHA256  # 或 MD5
  timestamp-window: 60
  nonce-expire-time: 60
  salt: your-secret-salt-key-change-in-production
```

### 2. 生成签名示例

```bash
# 调用签名生成接口
curl -X POST http://localhost:17680/api/user/signature/demo \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "action": "create"
  }'
```

响应示例：
```json
{
  "signatureType": "HMAC签名",
  "algorithm": "SHA256",
  "params": {
    "username": "testuser",
    "email": "test@example.com",
    "action": "create"
  },
  "headers": {
    "X-Timestamp": "1642752000",
    "X-Nonce": "abc123def456",
    "X-Sign": "1c674bffb850d102345ef29e3b8537dec0a6a4f71ac9e41d2d27b5c7402c8acb"
  },
  "signatureStandard": {
    "formula": "Sign = SHA256(业务参数 + Timestamp + Nonce + Salt)",
    "timestampWindow": "1分钟",
    "noncePolicy": "1分钟内同一Nonce只能使用一次"
  },
  "usage": "将headers中的值设置到HTTP请求头中：X-Timestamp, X-Nonce, X-Sign"
}
```

### 3. 使用签名调用接口

```bash
curl -X POST http://localhost:17680/api/user/create \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: 1642752000" \
  -H "X-Nonce: abc123def456" \
  -H "X-Sign: 1c674bffb850d102345ef29e3b8537dec0a6a4f71ac9e41d2d27b5c7402c8acb" \
  -d '{"username":"testuser","email":"test@example.com"}'
```

## 💻 客户端实现

### Java客户端

```java
public class HMACSignatureClient {
    private final String salt;
    private final String algorithm;
    
    public HMACSignatureClient(String salt, String algorithm) {
        this.salt = salt;
        this.algorithm = algorithm;
    }
    
    public Map<String, String> generateHeaders(Map<String, Object> params) {
        // 生成时间戳（秒级）
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        
        // 生成随机数
        String nonce = UUID.randomUUID().toString().replace("-", "");
        
        // 生成签名
        String sign = generateSign(params, timestamp, nonce);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Timestamp", timestamp);
        headers.put("X-Nonce", nonce);
        headers.put("X-Sign", sign);
        
        return headers;
    }
    
    private String generateSign(Map<String, Object> params, String timestamp, String nonce) {
        // 参数排序
        TreeMap<String, Object> sortedParams = new TreeMap<>(params);
        
        // 构建签名字符串
        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().trim().isEmpty()) {
                signStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }
        signStr.append("timestamp=").append(timestamp).append("&");
        signStr.append("nonce=").append(nonce).append("&");
        signStr.append("salt=").append(salt);
        
        // 计算签名
        if ("MD5".equalsIgnoreCase(algorithm)) {
            return DigestUtils.md5Hex(signStr.toString());
        } else {
            return DigestUtils.sha256Hex(signStr.toString());
        }
    }
}
```

### JavaScript客户端

```javascript
class HMACSignatureClient {
    constructor(salt, algorithm = 'SHA256') {
        this.salt = salt;
        this.algorithm = algorithm;
    }
    
    async generateHeaders(params) {
        // 生成时间戳（秒级）
        const timestamp = Math.floor(Date.now() / 1000).toString();
        
        // 生成随机数
        const nonce = this.generateUUID().replace(/-/g, '');
        
        // 生成签名
        const sign = await this.generateSign(params, timestamp, nonce);
        
        return {
            'X-Timestamp': timestamp,
            'X-Nonce': nonce,
            'X-Sign': sign
        };
    }
    
    async generateSign(params, timestamp, nonce) {
        // 参数排序
        const sortedKeys = Object.keys(params).sort();
        
        // 构建签名字符串
        let signStr = '';
        for (const key of sortedKeys) {
            if (params[key] != null && params[key].toString().trim() !== '') {
                signStr += `${key}=${params[key]}&`;
            }
        }
        signStr += `timestamp=${timestamp}&nonce=${nonce}&salt=${this.salt}`;
        
        // 计算签名
        if (this.algorithm === 'MD5') {
            return await this.md5(signStr);
        } else {
            return await this.sha256(signStr);
        }
    }
    
    async sha256(message) {
        const msgBuffer = new TextEncoder().encode(message);
        const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
    
    async md5(message) {
        // 需要引入MD5库，如crypto-js
        return CryptoJS.MD5(message).toString();
    }
    
    generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0;
            const v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }
}

// 使用示例
const client = new HMACSignatureClient('your-secret-salt', 'SHA256');
const headers = await client.generateHeaders({
    username: 'testuser',
    email: 'test@example.com'
});

// 发送请求
fetch('/api/user/create', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        ...headers
    },
    body: JSON.stringify({
        username: 'testuser',
        email: 'test@example.com'
    })
});
```

## 🔍 签名验证流程

### 服务端验证步骤

1. **参数完整性检查**
   ```java
   if (StrUtil.isBlank(timestamp) || StrUtil.isBlank(nonce) || StrUtil.isBlank(sign)) {
       return SignatureResult.fail("签名参数不完整");
   }
   ```

2. **时间戳校验**
   ```java
   long requestTime = Long.parseLong(timestamp);
   long currentTime = System.currentTimeMillis() / 1000;
   long timeDiff = Math.abs(currentTime - requestTime);
   
   if (timeDiff > 60) {
       return SignatureResult.fail("时间戳校验失败");
   }
   ```

3. **随机数防重放校验**
   ```java
   String key = "signature:nonce:" + nonce;
   String exists = redisTemplate.opsForValue().get(key);
   
   if (StrUtil.isNotBlank(exists)) {
       return SignatureResult.fail("随机数校验失败，可能存在重放攻击");
   }
   ```

4. **签名校验**
   ```java
   String serverSign = SignatureUtil.generateSign(params, timestamp, nonce, salt, algorithm);
   if (!serverSign.equals(clientSign)) {
       return SignatureResult.fail("签名校验失败");
   }
   ```

5. **记录随机数**
   ```java
   redisTemplate.opsForValue().set(key, "1", 60, TimeUnit.SECONDS);
   ```

## 📋 接口说明

### 需要签名验证的接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/user/create` | POST | 创建用户 |
| `/api/user/modify` | POST | 修改用户 |
| `/api/user/{id}` | DELETE | 删除用户 |

### 无需签名验证的接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/user/{id}` | GET | 查询用户 |
| `/api/user/signature/demo` | POST | 签名生成演示 |

## ⚠️ 安全注意事项

### 1. 盐值管理
- **生产环境**：使用强随机字符串作为Salt
- **密钥轮换**：定期更换Salt值
- **环境隔离**：不同环境使用不同Salt

### 2. 时间同步
- **服务器时间**：确保所有服务器时间同步
- **客户端时间**：客户端时间与服务端误差不超过1分钟
- **时区处理**：统一使用UTC时间

### 3. 随机数生成
- **唯一性**：确保随机数的唯一性
- **随机性**：使用安全的随机数生成器
- **长度**：建议随机数长度不少于32位

### 4. 传输安全
- **HTTPS**：生产环境必须使用HTTPS
- **请求头**：签名信息通过HTTP请求头传输
- **日志安全**：避免在日志中记录敏感信息

## 🔧 故障排查

### 常见错误及解决方案

1. **签名参数不完整**
   ```
   错误：缺少X-Timestamp、X-Nonce或X-Sign请求头
   解决：确保客户端设置了所有必需的请求头
   ```

2. **时间戳校验失败**
   ```
   错误：客户端时间与服务端时间差超过1分钟
   解决：同步客户端和服务端时间
   ```

3. **随机数校验失败**
   ```
   错误：重复使用相同的随机数
   解决：确保每次请求使用不同的随机数
   ```

4. **签名校验失败**
   ```
   错误：客户端和服务端签名不匹配
   解决：检查参数排序、Salt值、算法是否一致
   ```

### 调试技巧

1. **启用调试日志**
   ```yaml
   logging:
     level:
       com.chandler.security.example: DEBUG
   ```

2. **对比签名字符串**
   ```java
   System.out.println("客户端签名字符串: " + clientSignString);
   System.out.println("服务端签名字符串: " + serverSignString);
   ```

3. **验证参数排序**
   ```java
   TreeMap<String, Object> sortedParams = new TreeMap<>(params);
   System.out.println("排序后参数: " + sortedParams);
   ```

## 🎯 最佳实践

1. **统一时间格式**：使用Unix时间戳（秒级）
2. **参数过滤**：忽略空值和空字符串参数
3. **编码统一**：使用UTF-8编码
4. **错误处理**：提供明确的错误信息
5. **性能优化**：合理设置Redis过期时间
6. **监控告警**：监控签名验证失败率

通过以上标准实现，你的系统将具备完整的防篡改和防重放能力！