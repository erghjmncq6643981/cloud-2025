# RSA数字签名与加解密完整指南

## 🎯 功能概述

本系统实现了完整的RSA加解密和数字签名功能，支持两种模式：

1. **标准RSA数字签名**：私钥签名，公钥验证（推荐）
2. **前端私钥加密模式**：私钥加密，公钥解密（特殊场景）

## 🔐 核心特性

### RSA数字签名
- **算法**：SHA256withRSA（可配置）
- **密钥长度**：2048位
- **签名流程**：`Sign = RSA_Sign(业务参数 + Timestamp + Nonce)`
- **验证流程**：使用公钥验证签名完整性

### 前端私钥加密
- **加密方式**：RSA/ECB/PKCS1Padding
- **使用场景**：前端私钥加密敏感数据，后端公钥解密
- **安全性**：结合时间戳和随机数防重放

## 📋 系统架构

### 核心组件

1. **RSAUtil** - RSA工具类
   - 密钥对生成
   - 加解密功能
   - 数字签名功能

2. **RSAConfig** - RSA配置类
   - 启用/禁用RSA功能
   - 配置签名算法
   - 设置密钥信息

3. **RSASignatureClient** - 客户端签名工具
   - 生成签名请求头
   - 支持两种签名模式

4. **SignatureService** - 签名验证服务
   - 统一的签名验证入口
   - 支持HMAC和RSA双模式

## 🚀 快速开始

### 1. 生成RSA密钥对

```bash
# 调用密钥生成接口
curl -X POST http://localhost:17680/api/rsa/generate-keypair
```

响应示例：
```json
{
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMI...",
  "privateKey": "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKk...",
  "usage": {
    "publicKey": "用于后端验证签名和解密",
    "privateKey": "用于前端生成签名和加密"
  }
}
```

### 2. 配置RSA参数

在 `application.yaml` 中配置：

```yaml
rsa:
  enabled: true
  signature-algorithm: SHA256withRSA
  use-private-key-encryption: true  # 启用前端私钥加密模式
  public-key: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMI...  # 后端公钥
  private-key: MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKk... # 前端私钥（仅用于演示）
```

### 3. 前端生成签名

#### Java客户端示例

```java
// 标准RSA数字签名
RSASignatureClient client = new RSASignatureClient(privateKey, "SHA256withRSA");

// 前端私钥加密模式
RSASignatureClient client = new RSASignatureClient(privateKey, true);

// 生成签名
Map<String, Object> params = new HashMap<>();
params.put("username", "testuser");
params.put("action", "create");

RSASignatureClient.SignatureHeaders headers = client.generateHeaders(params);
Map<String, String> httpHeaders = headers.toHeaderMap();
```

#### JavaScript客户端示例

```javascript
// 使用crypto-js或node-rsa库
const NodeRSA = require('node-rsa');

function generateRSASignature(params, privateKeyPem) {
    // 1. 生成时间戳和随机数
    const timestamp = Math.floor(Date.now() / 1000).toString();
    const nonce = generateUUID();
    
    // 2. 构建签名字符串
    const sortedParams = Object.keys(params).sort().reduce((result, key) => {
        result[key] = params[key];
        return result;
    }, {});
    
    let signString = '';
    for (const [key, value] of Object.entries(sortedParams)) {
        signString += `${key}=${value}&`;
    }
    signString += `timestamp=${timestamp}&nonce=${nonce}`;
    
    // 3. RSA签名
    const key = new NodeRSA(privateKeyPem);
    const signature = key.sign(signString, 'base64');
    
    return {
        'X-Timestamp': timestamp,
        'X-Nonce': nonce,
        'X-Sign': signature
    };
}
```

### 4. 发送签名请求

```bash
curl -X POST http://localhost:17680/api/user/create \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: 1642752000" \
  -H "X-Nonce: abc123def456" \
  -H "X-Sign: hbE62KsS9/wJ4fWlKl3OLU8b..." \
  -d '{"username":"testuser","email":"test@example.com"}'
```

## 🔧 API接口说明

### RSA工具接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/rsa/generate-keypair` | POST | 生成RSA密钥对 |
| `/api/rsa/encrypt-with-private-key` | POST | 私钥加密 |
| `/api/rsa/decrypt-with-public-key` | POST | 公钥解密 |
| `/api/rsa/generate-signature` | POST | 生成RSA数字签名 |
| `/api/rsa/verify-signature` | POST | 验证RSA数字签名 |
| `/api/rsa/signature-demo` | POST | RSA接口签名演示 |

### 签名验证接口

需要RSA签名验证的接口：
- `POST /api/user/create` - 创建用户
- `POST /api/user/modify` - 修改用户  
- `DELETE /api/user/{id}` - 删除用户

## 🛡️ 安全机制

### 防重放攻击
1. **时间戳校验**：60秒时间窗口
2. **随机数校验**：Redis存储，防止重复使用
3. **签名完整性**：RSA签名确保数据未被篡改

### 密钥管理
1. **公钥**：部署在后端，用于验证签名和解密
2. **私钥**：分发给前端，用于生成签名和加密
3. **密钥轮换**：定期更换密钥对提高安全性

## 📊 性能对比

| 签名方式 | 签名速度 | 验证速度 | 安全级别 | 适用场景 |
|----------|----------|----------|----------|----------|
| HMAC | 极快 | 极快 | 高 | 内部系统 |
| RSA数字签名 | 慢 | 快 | 极高 | 对外API |
| RSA私钥加密 | 慢 | 慢 | 高 | 特殊需求 |

## 🔍 故障排查

### 常见错误

1. **RSA签名校验失败**
   - 检查私钥是否正确
   - 确认签名算法一致
   - 验证参数排序是否正确

2. **私钥加密解密失败**
   - 检查密钥对是否匹配
   - 确认加密数据格式正确
   - 验证Base64编码是否正确

3. **时间戳校验失败**
   - 同步客户端和服务端时间
   - 检查时间戳格式（秒级）
   - 确认时间窗口配置

### 调试技巧

1. **启用详细日志**
```yaml
logging:
  level:
    com.chandler.security.example: DEBUG
```

2. **使用测试接口验证**
```bash
# 测试RSA功能
curl -X POST http://localhost:17680/api/rsa/signature-demo \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test",
    "privateKey": "your-private-key",
    "usePrivateKeyEncryption": true
  }'
```

## 🚀 生产环境部署

### 安全配置

1. **密钥安全**
   - 私钥不要存储在后端代码中
   - 使用环境变量或密钥管理服务
   - 定期轮换密钥对

2. **网络安全**
   - 强制使用HTTPS
   - 配置防火墙规则
   - 启用访问日志监控

3. **性能优化**
   - 配置Redis集群
   - 启用连接池
   - 监控签名验证性能

### 配置示例

```yaml
# 生产环境配置
rsa:
  enabled: true
  signature-algorithm: SHA256withRSA
  use-private-key-encryption: true
  public-key: ${RSA_PUBLIC_KEY}  # 从环境变量读取
  
signature:
  enabled: false  # 禁用HMAC，使用RSA
  timestamp-window: 30  # 缩短时间窗口
  nonce-expire-time: 30
```

## 📚 扩展功能

### 自定义签名算法

```java
// 支持其他RSA签名算法
rsa:
  signature-algorithm: SHA1withRSA    # SHA1
  signature-algorithm: SHA256withRSA  # SHA256（推荐）
  signature-algorithm: SHA512withRSA  # SHA512
```

### 混合签名模式

```java
// 同时支持HMAC和RSA签名
signature:
  enabled: true   # 启用HMAC作为备选
  
rsa:
  enabled: true   # 启用RSA作为主要方式
```

## 🎯 最佳实践

1. **优先使用标准RSA数字签名**，而非私钥加密模式
2. **定期轮换密钥对**，建议每6个月更换一次
3. **监控签名验证失败率**，及时发现异常攻击
4. **使用HTTPS传输**，确保密钥和签名安全
5. **实施访问频率限制**，防止暴力破解攻击

通过以上配置，你的系统将具备企业级的RSA数字签名和加解密能力！