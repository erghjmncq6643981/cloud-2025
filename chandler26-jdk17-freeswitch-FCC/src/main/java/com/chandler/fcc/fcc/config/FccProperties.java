package com.chandler.fcc.fcc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fcc")
public class FccProperties {
    private String natsUrl = "nats://127.0.0.1:4222";
    private String defaultNodeId = "qiandingjundeMacBook-Pro.local";
    private String ctrlUuidPrefix = "fcc-ctrl";
    private long rpcTimeoutMillis = 5000;
    private String sidecarHttpUrl = "http://127.0.0.1:8088";
}
