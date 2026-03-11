# UserController RSA签名功能使用指南

## 🎯 功能概述

UserController已升级为使用RSA SHA256withRSA算法进行数字签名，提供更高的安全性和标准化的签名机制。

## 🔧 主要改进

### 1. 签名算法升级
- **原来**：HMAC SHA256（对称加密）
- **现在**：RSA SHA256withRSA（非对称加密）

### 2. 新增功能
- 自动密钥对生成
- 智能配置检测
- 详细的使用示例
- curl命令生成

## 🚀 使用方式

### 1. 生成RSA密钥对

```bash
# 调用密钥生成接口
curl -X POST http://localhost:17680/api/user/generate-keypair
```

响应示例：
```json
{
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMI...",
  "privateKey": "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKk...",
  "algorithm": "SHA256withRSA",
  "usage": {
    "publicKey": "配置到后端 application.yaml 的 rsa.public-key",
    "privateKey": "用于前端生成签名，不要存储在后端"
  },
  "configExample": "# 在 application.yaml 中配置\nrsa:\n  enabled: true\n  signature-algorithm: SHA256withRSA\n  public-key: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMI...\n  use-private-key-encryption: true"
}
```

### 2. 配置RSA参数

在 `application.yaml` 中添加配置：

```yaml
rsa:
  enabled: true
  signature-algorithm: SHA256withRSA
  use-private-key-encryption: true  # 启用前端私钥加密模式
  public-key: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMI...  # 从步骤1获取
  # private-key 不要配置在后端，仅用于前端
```

### 3. 生成签名示例

```bash
# 调用签名生成演示接口
curl -X POST http://localhost:17680/api/user/signature/demo \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "privateKey": "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKk..."
  }'
```

响应示例：
```json
{
  "signatureType": "标准RSA数字签名（SHA256withRSA）",
  "algorithm": "SHA256withRSA",
  "params": {
    "username": "testuser",
    "email": "test@example.com"
  },
  "headers": {
    "X-Timestamp": "1642752000",
    "X-Nonce": "abc123def456",
    "X-Sign": "eHTrxQoR/KWpOcYDR/o0FcoKfohThHxOLDVW..."
  },
  "usage": "将headers中的值设置到HTTP请求头中：X-Timestamp, X-Nonce, X-Sign",
  "example": {
    "curl": "curl -X POST http://localhost:17680/api/user/create \\\n  -H \"Content-Type: application/json\" \\\n  -H \"X-Timestamp: 1642752000\" \\\n  -H \"X-Nonce: abc123def456\" \\\n  -H \"X-Sign: eHTrxQoR/KWpOcYDR/o0FcoKfohThHxOLDVW...\" \\\n  -d '{\"username\":\"testuser\",\"email\":\"test@example.com\"}'"
  }
}
```

### 4. 使用签名调用接口

```bash
# 使用生成的签名调用创建用户接口
curl -X POST http://localhost:17680/api/user/create \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: 1642752000" \
  -H "X-Nonce: abc123def456" \
  -H "X-Sign: eHTrxQoR/KWpOcYDR/o0FcoKfohThHxOLDVW..." \
  -d '{"username":"testuser","email":"test@example.com"}'
```

## 🔄 两种签名模式

### 1. 标准RSA数字签名（推荐）

```yaml
rsa:
  use-private-key-encryption: false  # 使用标准模式
```

**特点**：
- 符合RSA数字签名标准
- 私钥签名，公钥验证
- 性能较好，安全性高

### 2. 前端私钥加密模式

```yaml
rsa:
  use-private-key-encryption: true  # 使用加密模式
```

**特点**：
- 前端私钥加密，后端公钥解密
- 适用于特殊安全需求
- 性能稍低，但提供额外保护

## 📋 接口说明

### 需要RSA签名的接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/user/create` | POST | 创建用户 |
| `/api/user/modify` | POST | 修改用户 |
| `/api/user/{id}` | DELETE | 删除用户 |

### 无需签名的接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/user/{id}` | GET | 查询用户 |
| `/api/user/signature/demo` | POST | 签名生成演示 |
| `/api/user/generate-keypair` | POST | 生成密钥对 |

## 🔍 前端集成示例

### JavaScript示例

```javascript
// 使用node-rsa库
const NodeRSA = require('node-rsa');

class RSASignatureClient {
    constructor(privateKeyPem, algorithm = 'SHA256withRSA') {
        this.privateKey = new NodeRSA(privateKeyPem);
        this.algorithm = algorithm;
    }
    
    generateHeaders(params) {
        // 1. 生成时间戳和随机数
        const timestamp = Math.floor(Date.now() / 1000).toString();
        const nonce = this.generateUUID();
        
        // 2. 构建签名字符串
        const signString = this.buildSignString(params, timestamp, nonce);
        
        // 3. RSA签名
        const signature = this.privateKey.sign(signString, 'base64');
        
        return {
            'X-Timestamp': timestamp,
            'X-Nonce': nonce,
            'X-Sign': signature
        };
    }
    
    buildSignString(params, timestamp, nonce) {
        // 参数排序
        const sortedKeys = Object.keys(params).sort();
        let signStr = '';
        
        // 拼接业务参数
        for (const key of sortedKeys) {
            if (params[key] != null && params[key].toString().trim() !== '') {
                signStr += `${key}=${params[key]}&`;
            }
        }
        
        // 添加时间戳和随机数
        signStr += `timestamp=${timestamp}&nonce=${nonce}`;
        
        return signStr;
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
const client = new RSASignatureClient(privateKeyPem);
const headers = client.generateHeaders({
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

### Java客户端示例

```java
// 使用RSASignatureClient
RSASignatureClient client = new RSASignatureClient(privateKey, "SHA256withRSA");

Map<String, Object> params = new HashMap<>();
params.put("username", "testuser");
params.put("email", "test@example.com");

RSASignatureClient.SignatureHeaders headers = client.generateHeaders(params);

// 设置HTTP请求头
HttpHeaders httpHeaders = new HttpHeaders();
httpHeaders.setAll(headers.toHeaderMap());

// 发送请求
RestTemplate restTemplate = new RestTemplate();
HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, httpHeaders);
ResponseEntity<String> response = restTemplate.postForEntity(
    "http://localhost:17680/api/user/create", 
    entity, 
    String.class
);
```

## ⚠️ 注意事项

1. **私钥安全**：私钥只能在前端使用，不要存储在后端代码中
2. **时间同步**：确保客户端和服务端时间同步，误差不超过60秒
3. **随机数唯一**：每次请求必须使用不同的随机数
4. **HTTPS传输**：生产环境必须使用HTTPS传输
5. **密钥轮换**：定期更换密钥对提高安全性

## 🔧 故障排查

### 常见错误

1. **RSA签名校验失败**
   ```
   解决方案：
   - 检查私钥格式是否正确
   - 确认签名算法配置一致
   - 验证参数排序逻辑
   ```

2. **未配置私钥错误**
   ```
   响应：{"message": "未配置私钥，已生成新的密钥对用于演示"}
   解决方案：使用返回的密钥对进行配置
   ```

3. **时间戳校验失败**
   ```
   解决方案：
   - 同步系统时间
   - 检查时间戳格式（秒级）
   ```

通过以上配置，你的系统将具备企业级的RSA数字签名能力！