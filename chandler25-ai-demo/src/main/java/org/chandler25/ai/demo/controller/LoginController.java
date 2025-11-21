package org.chandler25.ai.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.ai.demo.common.UserStatus;
import org.chandler25.ai.demo.common.UserType;
import org.chandler25.ai.demo.domain.bo.LoginInfo;
import org.chandler25.ai.demo.domain.dto.RegisterUserDTO;
import org.chandler25.ai.demo.domain.dto.UserDTO;
import org.chandler25.ai.demo.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Objects;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 13:28
 * @version 1.0.0
 * @since 21
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "用户登录接口", description = "不需要携带token")
public class LoginController {
    @Value("${login.key:123456}")
    private String key;
    private final UserService userService;

    @Operation(summary = "登录", description = "登录认证成功之后会返回有效期为1小时的token，过期之前可以续约")
    @PostMapping("/login")
    public String login(@RequestHeader("loginKey") String loginKey, @RequestBody LoginInfo userDTO) {
        if (!Objects.equals(loginKey, key)) {
            throw new RuntimeException("loginKey无效");
        }
        return userService.login(userDTO.getLoginName(), userDTO.getLoginPassword());
    }

    @Operation(summary = "续约", description = "token，过期之前可以续约，token需要放在Authorization中")
    @GetMapping("/login/renewal")
    public String renewal(@RequestParam("loginName") String loginName,
                          @RequestParam("loginPassword") String loginPassword,
                          @RequestHeader("Authorization") String token) {
        return userService.renewal(loginName, loginPassword, token);
    }

    @Operation(summary = "注册用户", description = "注册用户")
    @PostMapping("/register")
    public void register(@Valid @RequestBody RegisterUserDTO u){
        if(StringUtils.isBlank(u.getLoginName())&&StringUtils.isBlank(u.getPhoneNumber())){
            throw new RuntimeException("请输入登录名或者手机号码!");
        }else{
            if(StringUtils.isBlank(u.getLoginName())){
                u.setPhoneNumber(u.getPhoneNumber());
            }
        }
        UserDTO user=new UserDTO();
        BeanUtils.copyProperties(u,user);
        user.setUserType(UserType.VISITOR);
        user.setUserStatus(UserStatus.ENABLED);
        userService.add(user);
    }
}