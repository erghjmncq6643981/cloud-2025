package com.chandler.freeswitch.client.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * FreeSWITCH ESL配置
 *
 * @author chandler
 * @since 1.0
 */
@Slf4j
@Configuration
public class EslConfig {
    
    @Value("${freeswitch.esl.host:localhost}")
    private String host;
    
    @Value("${freeswitch.esl.port:8021}")
    private int port;
    
    @Value("${freeswitch.esl.password:ClueCon}")
    private String password;
    
    @Value("${freeswitch.esl.timeout:5000}")
    private int timeout;
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getPassword() {
        return password;
    }
    
    public int getTimeout() {
        return timeout;
    }
}
