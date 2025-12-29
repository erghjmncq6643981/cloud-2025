package com.chandler.gateway.example.filter;

import cn.dev33.satoken.same.SaSameUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.chandler.gateway.example.config.properties.TokenFilterProperties;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * 临时Token局部过滤器
 * 根据来源域名/IP放行，并添加 sa-token 的临时token
 * 支持PC端（通过Origin header）和Node端（通过RemoteAddress）调用
 *
 * @author 钱丁君-chandler 2025/12/10
 */
@Slf4j
@Order(0)
@Component
public class TemporaryTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<TemporaryTokenGatewayFilterFactory.Config> {

    private static final String DEFAULT_FILTER_NAME = "TemporaryToken";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    @Autowired
    private TokenFilterProperties tokenFilterProperties;

    public TemporaryTokenGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public String name() {
        return DEFAULT_FILTER_NAME;
    }

    @Override
    public GatewayFilter apply(Config config) {
        // 初始化配置中的域名列表
        List<String> domainNames = CollectionUtil.isNotEmpty(tokenFilterProperties.getDomainNames())
                ? tokenFilterProperties.getDomainNames().stream().filter(StringUtils::isNotBlank).toList()
                : Lists.newArrayList();
        List<String> excludeDomainNames = CollectionUtil.isNotEmpty(tokenFilterProperties.getExcludeDomainNames())
                ? tokenFilterProperties.getExcludeDomainNames().stream().filter(StringUtils::isNotBlank).toList()
                : Lists.newArrayList();

        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String method = request.getMethod().name();

            // 解析来源：优先从 Origin 获取（PC端），否则从 RemoteAddress 获取（Node端）
            String host = resolveHost(request);
            log.debug("临时Token过滤器 - 来源host:{}, path:{}, method:{}", host, path, method);

            if (StringUtils.isBlank(host)) {
                return chain.filter(exchange);
            }

            // 检查是否在排除列表中
            if (excludeDomainNames.contains(host)) {
                log.debug("来源在排除列表中，跳过临时Token添加: {}", host);
                return chain.filter(exchange);
            }

            // 检查是否在允许列表中
            if (!domainNames.contains(host)) {
                log.debug("来源不在允许列表中，跳过临时Token添加: {}", host);
                return chain.filter(exchange);
            }

            // 检查是否匹配需要放行的API
            if (needToken(config.getApis(), method, path)) {
                log.debug("匹配临时Token规则，添加内网标识进行放行！host:{}, path:{}", host, path);
                ServerHttpRequest newRequest = request.mutate()
                        .header(SaSameUtil.SAME_TOKEN, SaSameUtil.getToken())
                        .build();
                return chain.filter(exchange.mutate().request(newRequest).build());
            }

            return chain.filter(exchange);
        };
    }

    /**
     * 解析请求来源Host
     * PC端：从 Origin header 中获取
     * Node端：从 RemoteAddress 中获取
     */
    private String resolveHost(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        log.debug("获取请求中的来源信息，headers:{}", headers);
        InetSocketAddress clientAddress = request.getLocalAddress();
        // 从 RemoteAddress 获取（Node端直接调用）
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        log.debug("获取请求中的来源信息，URI:{} clientAddress:{} remoteAddress:{}", request.getURI() ,clientAddress, remoteAddress);
        // 优先从 Origin 获取（PC端浏览器请求）
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (StringUtils.isNotBlank(origin)) {
            try {
                URI uri = URI.create(origin);
                String host = uri.getHost();
                log.debug("从Origin解析host: {}", host);
                return host;
            } catch (Exception e) {
                log.warn("解析Origin失败: {}", origin, e);
            }
        }
        if (clientAddress != null) {
            String host = clientAddress.getHostString();
            log.debug("从clientAddress中解析host: {}", host);
            return host;
        }

        return null;
    }

    /**
     * 检查是否需要添加临时Token
     */
    private boolean needToken(List<DestApi> apis, String method, String path) {
        if (CollectionUtil.isEmpty(apis)) {
            // 如果没有配置API列表，默认放行所有
            return true;
        }
        return apis.stream()
                .anyMatch(api -> Objects.equals(method, api.getMethod()) &&
                        CollectionUtil.isNotEmpty(api.getPaths()) &&
                        api.getPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path)));
    }

    /**
     * 过滤器配置类
     */
    @Data
    public static class Config {
        /**
         * 需要放行的API列表，为空则放行所有匹配路由的请求
         */
        private List<DestApi> apis = Lists.newArrayList();
    }

    /**
     * 目标API配置
     */
    @Data
    public static class DestApi {
        /**
         * HTTP方法: GET, POST, PUT, DELETE 等
         */
        private String method;

        /**
         * 路径模式列表，支持AntPath格式，如 /api/**, /user/*
         */
        private List<String> paths;
    }
}
