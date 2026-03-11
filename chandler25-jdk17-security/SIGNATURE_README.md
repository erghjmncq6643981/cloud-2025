# 应用层防篡改与防重放全链路接口签名机制

## 功能概述

本系统实现了基于签名的接口安全验证机制，包含以下核心功能：

- **防篡改**：通过签名验证确保请求参数未被篡改
- **防重放**：通过时间戳和随机数机制防止请求重放攻击
- **全链路保护**：支持GET/POST/PUT/DELETE等所有HTTP方法

## 签名算法

### 签名生成规则
```
Sign = SHA256/MD5(业务参数 + Timestamp + Nonce + Salt)
```

### 参数说明
- **业务参数**：按字典序排序的请求参数
- **Timestamp**：Unix时间戳（秒级）
- **Nonce**：随机数，用于防重放
- **Salt**：服务端密钥

## 防重放机制

### 时间戳校验
- 服务端校验窗口：60秒（可配置）
- 超出时间窗口的请求将被拒绝

### 随机数校验
- 使用Redis存储已使用的Nonce
- 60秒内同一Nonce只能使用一次
- 彻底杜绝抓包重放攻击

## 配置说明

### application.yaml配置
```yaml
signature:
  enabled: true                    # 是否启用签名验证
  algorithm: SHA256               # 签名算法：MD5/SHA256
  timestamp-window: 60            # 时间戳校验窗口（秒）
  nonce-expire-time: 60          # 随机数过期时间（秒）
  salt: your-secret-salt-key     # 签名盐值（生产环境请修改）
```

## 使用方式

### 1. 服务端配置

#### 启用签名验证
在需要签名验证的接口上添加 `@RequireSignature` 注解：

```java
@PostMapping("/create")
@RequireSignature
public void createUser(@RequestBody User user) {
    // 业务逻辑
}
```

#### 全局拦截器配置
拦截器已自动配置，默认拦截 `/api/**` 路径下的所有请求。

### 2. 客户端请求

#### 请求头设置
客户端需要在HTTP请求头中设置以下参数：

```
X-Timestamp: 1642752000    # Unix时间戳（秒）
X-Nonce: abc123def456      # 随机数
X-Sign: a1b2c3d4e5f6...    # 签名值
```

#### Java客户端示例

```java
// 1. 创建签名客户端
SignatureClient client = new SignatureClient("your-salt", "SHA256");

// 2. 准备请求参数
Map<String, Object> params = new HashMap<>();
params.put("username", "testuser");
params.put("email", "test@example.com");

// 3. 生成签名请求头
SignatureClient.SignatureHeaders headers = client.generateHeaders(params);

// 4. 发送HTTP请求
HttpHeaders httpHeaders = new HttpHeaders();
httpHeaders.setAll(headers.toHeaderMap());
// ... 发送请求
```

#### curl示例

```bash
# 1. 先调用签名生成接口获取签名
curl -X POST http://localhost:17680/api/user/signature/demo \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com"}'

# 2. 使用返回的签名信息发送请求
curl -X POST http://localhost:17680/api/user/create \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: 1642752000" \
  -H "X-Nonce: abc123def456" \
  -H "X-Sign: generated_signature_here" \
  -d '{"username":"testuser","email":"test@example.com"}'
```

## 签名生成步骤详解

### 1. 参数排序
将所有业务参数按key进行字典序排序：

```java
TreeMap<String, Object> sortedParams = new TreeMap<>(params);
```

### 2. 构建签名字符串
按照以下格式拼接字符串：

```
key1=value1&key2=value2&timestamp=1642752000&nonce=abc123&salt=your-salt
```

### 3. 计算签名
使用指定算法计算签名：

```java
String signature = DigestUtil.sha256Hex(signString);
```

## 测试验证

### 运行测试用例
```bash
mvn test -Dtest=SignatureTest
```

### 使用Swagger测试
1. 启动应用：`mvn spring-boot:run`
2. 访问：http://localhost:17680/swagger-ui.html
3. 使用 `/api/user/signature/demo` 接口生成签名
4. 使用生成的签名测试其他接口

## 安全建议

### 生产环境配置
1. **修改Salt值**：使用强随机字符串作为Salt
2. **HTTPS传输**：确保所有API调用使用HTTPS
3. **密钥管理**：将Salt配置在环境变量或配置中心
4. **监控告警**：监控签名验证失败的请求

### 性能优化
1. **Redis连接池**：配置合适的Redis连接池参数
2. **缓存策略**：合理设置Nonce过期时间
3. **异步处理**：考虑异步记录签名验证日志

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 401 | 签名参数不完整 |
| 401 | 时间戳校验失败 |
| 401 | 随机数校验失败，可能存在重放攻击 |
| 401 | 签名校验失败 |

## 常见问题

### Q: 时间戳校验失败？
A: 检查客户端和服务端时间是否同步，确保时间差在60秒内。

### Q: 随机数重复？
A: 确保每次请求使用不同的随机数，推荐使用UUID。

### Q: 签名不匹配？
A: 检查参数排序、Salt值、算法类型是否一致。

## 扩展功能

### 自定义时间窗口
```java
@RequireSignature(timestampWindow = 120) // 自定义2分钟窗口
public void customMethod() {
    // 业务逻辑
}
```

### 跳过签名验证
```java
@RequireSignature(required = false)
public void publicMethod() {
    // 不需要签名验证的接口
}
```