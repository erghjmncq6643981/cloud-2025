package com.chandler.key.example.controller;

import com.chandler.key.example.domain.dataobject.User;
import com.chandler.key.example.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * restful风格的接口
 *
 * @author 钱丁君-chandler 2019/5/17下午2:00
 * @since 1.8
 */
@Tag(name = "示例接口", description = "提供示例内容展示SpringDoc集成效果")
@RestController
@RequestMapping("user")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(description = "查询")
    @GetMapping("/{id}")
    public  User getUser(@RequestParam("id") Long id) {
        return userService.detail(id);
    }

    @Operation(description = "新增")
    @PostMapping("create")
    public void add(@RequestBody User user) {
        userService.add(user);
    }

    @Operation(description = "删除")
    @DeleteMapping("{id}")
    public void delete(@PathVariable("id") Long id) {
        userService.delete(id);
    }

    @Operation(description = "修改")
    @PutMapping("modify")
    public  void update(User user) {
        userService.update(user);
    }
}
