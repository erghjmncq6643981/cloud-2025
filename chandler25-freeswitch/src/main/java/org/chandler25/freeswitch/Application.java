package org.chandler25.freeswitch;

import link.thingscloud.freeswitch.esl.spring.boot.starter.EnableFreeswitchEslAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 类功能描述
 *
 * @version 1.0.0  
 * @author 钱丁君-chandler 2025/7/18 15:35
 * @since 1.8
 */
//@EnableFreeswitchEslAutoConfiguration
@EnableDiscoveryClient
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
