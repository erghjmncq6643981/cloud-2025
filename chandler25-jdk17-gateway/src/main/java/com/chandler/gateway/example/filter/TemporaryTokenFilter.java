package com.chandler.gateway.example.filter;

import cn.dev33.satoken.same.SaSameUtil;
import cn.dev33.satoken.temp.SaTempUtil;
import com.chandler.gateway.example.feignclient.SatokenFeignClient;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.List;

/**
 *
 * @author 钱丁君-chandler 2025/12/10
 */
@Slf4j
@Component
public class TemporaryTokenFilter implements GlobalFilter, Ordered, SmartInitializingSingleton {
    private List<String> serverNames = Lists.newArrayList();

    private final SatokenFeignClient satokenFeignClient;

    public TemporaryTokenFilter(@Lazy SatokenFeignClient satokenFeignClient) {
        this.satokenFeignClient = satokenFeignClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        //检查是否为官网域名，访问的目的地是否为suit服务
        URI uri = request.getURI();
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();
        log.info("获取请求中的目的地信息，host:{} port:{}; path:{}", host, port, path);
       String remoteHostIp= request.getRemoteAddress().getAddress().getHostAddress();
        String remoteHostString=request.getRemoteAddress().getHostString();
        int remotePort=request.getRemoteAddress().getPort();
        log.info("获取请求中的来源信息，hostIp:{} hostString:{}; port:{}", remoteHostIp, remoteHostString, remotePort);
        //检查是否有token
        String authorization = "V4-Authorization";
        List<String> authorizationValues = request.getHeaders().get(authorization);
        if (authorizationValues != null) {
            return chain.filter(exchange);
        }

        ServerHttpRequest newRequest = exchange
                .getRequest()
                .mutate()
                // 为请求追加 Same-Token 参数
                .header(SaSameUtil.SAME_TOKEN, SaSameUtil.getToken())
                .build();
        ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
        return chain.filter(newExchange);

//        return Mono.fromCallable(() -> satokenFeignClient.temporaryToken("10012", "123456"))
//                .subscribeOn(Schedulers.boundedElastic())
//                .flatMap(token -> {
//                    // 4. 将令牌添加到请求头
//                    log.info("成功获取临时令牌: {}", token.substring(0, Math.min(token.length(), 20)) + "...");
//
//                    ServerHttpRequest modifiedRequest = request.mutate()
//                            .header("gateway-temporary-token", token)
//                            .build();
//
//                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
//                }).onErrorResume(e -> {
//                    // 5. 错误处理：记录日志但继续处理请求
//                    log.error("获取临时令牌失败: {}", e.getMessage());
//                    return chain.filter(exchange);
//                });
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void afterSingletonsInstantiated() {

    }
}
