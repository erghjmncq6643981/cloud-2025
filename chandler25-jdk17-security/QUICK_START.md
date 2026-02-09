# 快速启动指南

## 1. 启动应用

```bash
# 确保Redis已启动
# 启动Spring Boot应用
mvn spring-boot:run
```

## 2. 访问Swagger文档

打开浏览器访问：http://localhost:17680/swagger-ui.html

## 3. 测试签名功能

### 步骤1：生成签名
使用POST请求调用签名生成接口：

```bash
curl -X POST http://localhost:17680/api/user/signature/demo \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com"}'
```

响应示例：
```json
{
  "params": {
    "username": "testuser",
    "email": "test@example.com"
  },
  "headers": {
    "X-Timestamp": "1642752000",
    "X-Nonce": "abc123def456",
    "X-Sign": "generated_signature_here"
  },
  "usage": "将headers中的值设置到HTTP请求头中：X-Timestamp, X-Nonce, X-Sign"
}
```

### 步骤2：使用签名调用接口
使用返回的签名信息调用需要验证的接口：

```bash
curl -X POST http://localhost:17680/api/user/create \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: 1642752000" \
  -H "X-Nonce: abc123def456" \
  -H "X-Sign: generated_signature_here" \
  -d '{"username":"testuser","email":"test@example.com"}'
```

## 4. 测试防重放功能

尝试使用相同的签名再次调用接口，应该会收到"随机数校验失败"的错误。

## 5. 配置说明

在 `application.yaml` 中可以调整签名验证参数：

```yaml
signature:
  enabled: true                    # 启用/禁用签名验证
  algorithm: SHA256               # 签名算法
  timestamp-window: 60            # 时间窗口（秒）
  nonce-expire-time: 60          # 随机数过期时间（秒）
  salt: your-secret-salt-key     # 签名盐值（生产环境请修改）
```

## 6. 接口说明

- **无需签名验证的接口**：
  - GET `/api/user/{id}` - 查询用户
  - POST `/api/user/signature/demo` - 生成签名演示

- **需要签名验证的接口**：
  - POST `/api/user/create` - 创建用户
  - POST `/api/user/modify` - 修改用户
  - DELETE `/api/user/{id}` - 删除用户

## 7. 错误处理

常见错误及解决方案：

| 错误信息 | 原因 | 解决方案 |
|---------|------|----------|
| 签名参数不完整 | 缺少必要的请求头 | 确保设置了X-Timestamp、X-Nonce、X-Sign |
| 时间戳校验失败 | 时间差超过60秒 | 检查系统时间，使用当前时间戳 |
| 随机数校验失败 | 重复使用相同随机数 | 每次请求使用不同的随机数 |
| 签名校验失败 | 签名计算错误 | 检查参数顺序、盐值、算法是否正确 |

## 8. 生产环境部署

1. **修改盐值**：将配置中的salt改为强随机字符串
2. **启用HTTPS**：确保所有API调用使用HTTPS
3. **监控告警**：监控签名验证失败的请求
4. **Redis配置**：配置Redis集群以提高可用性