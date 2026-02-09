package com.chandler.security.example.config;

import com.chandler.security.example.interceptor.SignatureInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private SignatureInterceptor signatureInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(signatureInterceptor)
                .addPathPatterns("/api/**")  // 需要签名验证的路径
                .excludePathPatterns(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/**",
                        "/error",
                        "/api/user/signature/demo",  // 排除HMAC签名生成演示接口
                        "/api/rsa/**"  // 排除RSA相关接口
                );
    }
}