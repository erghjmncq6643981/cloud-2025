package org.chandler25.ai.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.chandler25.ai.demo.common.UserLoginCheckHelper;
import org.chandler25.ai.demo.domain.dto.UserDTO;
import org.chandler25.ai.demo.respository.entity.User;
import org.chandler25.ai.demo.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;


/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 10:25
 * @version 1.0.0
 * @since 21
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户接口", description = "用户接口")
public class UserController {

    private final UserService userService;

    @Operation(summary = "查询批量用户", description = "查询批量用户")
    @PostMapping("/query/list")
    public List<User> queryUsers(@RequestBody UserDTO u){
        return userService.queryUsers(u);
    }

    @Operation(summary = "查询单个用户", description = "查询单个用户")
    @GetMapping("/query/{id}")
    public User queryById(@PathVariable Long id){
        User u= userService.queryById(id);
        return u;
    }

    @Operation(summary = "新增or更新用户", description = "新增or更新用户")
    @PostMapping("/add")
    public void add(@Valid @RequestBody UserDTO u){
        userService.add(u);
    }

    @Operation(summary = "删除用户", description = "删除用户")
    @DeleteMapping("/del/{id}")
    public void del(Long id){
        userService.del(id);
    }

    @Operation(summary = "查询当前登录用户", description = "查询当前登录用户")
    @GetMapping("/query/loginInfo")
    public User queryUserInfo(){
        return UserLoginCheckHelper.getUserSession();
    }

}