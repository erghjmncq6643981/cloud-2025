package com.chandler.gateway.example.controller;

import com.chandler.gateway.example.domain.dataobject.User;
import com.chandler.gateway.example.service.UserService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * restful风格的接口
 *
 * @author 钱丁君-chandler 2019/5/17下午2:00
 * @since 1.8
 */
@RestController
public class RestServiceProvider {

    @Value("${server.port}")
    private String port;
    @Value("${spring.cloud.client.ip-address}")
    private String ipAddress;
    @Autowired
    private UserService userService;

    @ApiOperation(value = "get请求测试", notes = "获取ipAddress")
    @GetMapping("/user/detail")
    public User getHost(@RequestParam("id") Long id) {
        return userService.detail(id);
    }

    @RequestMapping(value = "/head/getHost", method = RequestMethod.GET)
    public String getHost(@ApiParam(value = "姓名", required = true, defaultValue = "chandler") @RequestParam("name") String name,
                          @ApiParam(value = "年龄", required = true, defaultValue = "18", example = "18") @RequestHeader int age) {
        return String.format("hi,%s, your age is %s! i from %s:%s", name, age, ipAddress, port);
    }
}
