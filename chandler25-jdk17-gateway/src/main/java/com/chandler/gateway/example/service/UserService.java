package com.chandler.gateway.example.service;

import com.chandler.gateway.example.domain.dataobject.User;
import com.chandler.gateway.example.domain.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 * @author 钱丁君-chandler 2025/12/5
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    public User detail(Long id ) {
        return userMapper.selectById(id);
    }
}
