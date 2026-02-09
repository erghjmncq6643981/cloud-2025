package com.chandler.security.example.interceptor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.chandler.security.example.service.SignatureService;
import com.chandler.security.example.util.SignatureUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 签名验证拦截器
 */
@Component
public class SignatureInterceptor implements HandlerInterceptor {
    
    @Autowired
    private SignatureService signatureService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取签名相关参数
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String sign = request.getHeader("X-Sign");
        
        System.out.println("签名验证 - timestamp: " + timestamp + ", nonce: " + nonce + ", sign: " + sign);
        
        // 获取业务参数
        Map<String, Object> params = extractParams(request);
        
        // 执行签名验证
        SignatureService.SignatureResult result = signatureService.verifySignature(params, timestamp, nonce, sign);
        
        if (!result.isSuccess()) {
            System.out.println("签名验证失败: " + result.getMessage() + ", 请求路径: " + request.getRequestURI());
            writeErrorResponse(response, result.getMessage());
            return false;
        }
        
        System.out.println("签名验证成功，请求路径: " + request.getRequestURI());
        return true;
    }
    
    /**
     * 提取请求参数
     * 
     * @param request HTTP请求
     * @return 参数Map
     */
    private Map<String, Object> extractParams(HttpServletRequest request) throws IOException {
        Map<String, Object> params = new HashMap<>();
        
        // 1. 获取URL参数
        if (request.getParameterMap() != null) {
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    params.put(key, values[0]);
                }
            });
        }
        
        // 2. 获取请求体参数（POST/PUT等）
        if ("POST".equalsIgnoreCase(request.getMethod()) || 
            "PUT".equalsIgnoreCase(request.getMethod()) ||
            "PATCH".equalsIgnoreCase(request.getMethod())) {
            
            String contentType = request.getContentType();
            if (StrUtil.isNotBlank(contentType) && contentType.contains("application/json")) {
                // 处理JSON请求体
                String body = getRequestBody(request);
                if (StrUtil.isNotBlank(body)) {
                    Map<String, Object> bodyParams = SignatureUtil.parseParams(body);
                    params.putAll(bodyParams);
                }
            }
        }
        
        return params;
    }
    
    /**
     * 获取请求体内容
     * 
     * @param request HTTP请求
     * @return 请求体字符串
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        try {
            return IoUtil.read(request.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("读取请求体失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 写入错误响应
     * 
     * @param response HTTP响应
     * @param message 错误消息
     */
    private void writeErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("message", message);
        result.put("data", null);
        
        response.getWriter().write(JSON.toJSONString(result));
    }
}