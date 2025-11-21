package org.chandler25.ai.demo.service;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.ai.demo.common.UserLoginCheckHelper;
import org.chandler25.ai.demo.domain.dto.UserDTO;
import org.chandler25.ai.demo.respository.entity.User;
import org.chandler25.ai.demo.respository.mapper.UserMapper;
import org.chandler25.ai.demo.util.Argon2PasswordUtil;
import org.chandler25.ai.demo.util.JwtUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static org.chandler25.ai.demo.respository.entity.table.UserTableDef.USER;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 10:26
 * @version 1.0.0
 * @since 21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;
    private final UserMapper userMapper;

    public String login(String loginName, String loginPassword) {
        QueryWrapper query = QueryWrapper.create()
                .select(USER.ALL_COLUMNS)
                .from(USER)
                .where(USER.LOGIN_NAME.eq(loginName))
                .limit(1);

        User user = userMapper.selectOneByQuery(query);
        if (Objects.isNull(user) || !Argon2PasswordUtil.verifyPassword(loginPassword, user.getLoginPassword())) {
            throw new RuntimeException(String.format("%s用户校验失败！", loginName));
        }
        return JwtUtil.generateToken(loginName, loginPassword, secret, expiration);
    }

    public boolean checkToken(String loginName, String token) {
        return JwtUtil.validateToken(token, secret, loginName);
    }

    public String renewal(String loginName, String loginPassword, String token) {
        if (checkToken(loginName, token)) {
            return login(loginName, loginPassword);
        }
        throw new RuntimeException("登录状态已过期，请重新登录");
    }

    public User queryById(Long id) {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.nonNull(loginUser)) {
            log.info("登录的用户是：{}", loginUser.getLoginName());
        }
        QueryWrapper query = QueryWrapper.create()
                .select(USER.ALL_COLUMNS)
                .from(USER)
                .where(USER.ID.eq(id));

        return userMapper.selectOneByQuery(query);
    }

    public User queryByLoginName(String name) {
        QueryWrapper query = QueryWrapper.create()
                .select(USER.ALL_COLUMNS)
                .from(USER)
                .where(USER.LOGIN_NAME.eq(name))
                .limit(1);

        return userMapper.selectOneByQuery(query);
    }

    public List<User> queryUsers(UserDTO u) {
        QueryWrapper query = QueryWrapper.create()
                .select(USER.ALL_COLUMNS)
                .from(USER);

        if (Objects.nonNull(u.getId())) {
            query.where(USER.ID.eq(u.getId()));
        }
        if (StringUtils.isNotBlank(u.getLoginName())) {
            query.where(USER.LOGIN_NAME.like(u.getLoginName() + "%"));
        }
        if (StringUtils.isNotBlank(u.getNickName())) {
            query.where(USER.NICK_NAME.like(u.getNickName() + "%"));
        }
        if (Objects.nonNull(u.getUserType())) {
            query.where(USER.USER_TYPE.eq(u.getUserType().name()));
        }
        query.orderBy(USER.ID, Boolean.FALSE);

        return userMapper.selectListByQuery(query);
    }

    @Transactional
    public void add(UserDTO u) {
        String loginUserName = "";
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            loginUserName = loginUser.getLoginName();
        }
        User user = new User();
        BeanUtils.copyProperties(u, user);
        user.setLastUpdateBy(loginUserName);
        user.setLoginPassword(Argon2PasswordUtil.hashPassword(user.getLoginPassword()));
        if (Objects.isNull(u.getId())) {
            if (Objects.nonNull(queryByLoginName(u.getLoginName()))) {
                throw new RuntimeException(String.format("登录名%s已经存在，可以直接登录", u.getLoginName()));
            }
            user.setCreateBy(loginUserName);
            userMapper.insert(user);
        } else {
            userMapper.update(user);
        }
    }

    public void del(Long id) {
        userMapper.deleteById(id);
    }
}