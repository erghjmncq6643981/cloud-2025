
package com.chandler.gateway.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 模板工程
 *
 * @version 1.0
 * @author 钱丁君-chandler 4/2/21 10:05 PM
 * @since 1.8
 */
@EnableFeignClients
@MapperScan("com.chandler.gateway.example.domain.mapper")
@EnableDiscoveryClient
@SpringBootApplication
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
