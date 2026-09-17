package com.chandler.fcc.fcc.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class FccNatsConfiguration {

    @Bean(destroyMethod = "close")
    public Connection natsConnection(FccProperties properties) throws Exception {
        log.info("🔌 [FCC] 连接 NATS 总线: {}", properties.getNatsUrl());
        Options options = new Options.Builder()
                .server(properties.getNatsUrl())
                .connectionName("chandler26-fcc-client")
                .connectionTimeout(Duration.ofSeconds(5))
                .pingInterval(Duration.ofSeconds(10))
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .connectionListener((conn, type) -> log.info("🔄 [FCC NATS] 状态变更: {}", type))
                .errorListener(new io.nats.client.ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("❌ [FCC NATS] 发生错误: {}", error);
                    }
                })
                .build();

        Connection connection = Nats.connect(options);
        log.info("✅ [FCC] 成功连接至 NATS 总线: {}", connection.getConnectedUrl());
        return connection;
    }
}
