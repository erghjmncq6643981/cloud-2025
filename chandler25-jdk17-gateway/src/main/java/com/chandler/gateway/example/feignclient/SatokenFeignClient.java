package com.chandler.gateway.example.feignclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author 钱丁君-chandler 2025/12/10
 */
@FeignClient(value = "satoken-demo",path = "/user")
public interface SatokenFeignClient {

    @GetMapping("/create/temporaryToken")
    public String temporaryToken(@RequestParam String username, @RequestParam String password);
}
