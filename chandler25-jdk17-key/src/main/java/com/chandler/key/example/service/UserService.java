package com.chandler.key.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.key.example.domain.dataobject.User;
import com.chandler.key.example.domain.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 * @author 钱丁君-chandler 2025/12/5
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    public List<User> findAll() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getId);
        return userMapper.selectList(wrapper);
    }

    public User detail(Long id ) {
        return userMapper.selectById(id);
    }

    public void add(User user) {
        userMapper.insert(user);
    }

    public void update(User user) {
        userMapper.updateById(user);
    }

    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    public User detailXml(Long id) {
        return userMapper.selectByIdXml(id);
    }

    public void createXml(User user) {
        userMapper.insertXml(user);
    }
}
