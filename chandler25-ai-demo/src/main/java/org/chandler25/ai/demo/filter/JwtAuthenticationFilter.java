package org.chandler25.ai.demo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.ai.demo.common.UserLoginCheckHelper;
import org.chandler25.ai.demo.config.properties.AuthProperties;
import org.chandler25.ai.demo.respository.entity.User;
import org.chandler25.ai.demo.service.UserService;
import org.chandler25.ai.demo.util.ExcludeURLRole;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.chandler25.ai.demo.util.JwtUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 13:38
 * @version 1.0.0
 * @since 21
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter implements InitializingBean {
    private static final String AUTH_FIELD_NAME = "Authorization";
    @Value("${jwt.secret}")
    private String secret;
    @Autowired
    private AuthProperties authProperties;
    @Autowired
    private UserService userService;
    private ExcludeURLRole excludeURLRole;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (Objects.nonNull(excludeURLRole) && excludeURLRole.findMatch(request.getRequestURI())) {
            String token = request.getHeader(AUTH_FIELD_NAME);
            if (Objects.isNull(token)) {
                log.warn("token 不存在，禁止访问。");
                outErrorMsg(response, "需要先登录");
                return;
            }

            if (!JwtUtil.validateToken(token, secret)) {
                log.warn("tokenId[{}]已无效，禁止访问。", token);
                outErrorMsg(response, "登录已失效");
                return;
            }
            String userName = JwtUtil.extractUsername(token, secret);
            if (StringUtils.isNotBlank(userName)) {
                User user = userService.queryByLoginName(userName);
                UserLoginCheckHelper.setUserSession(user);
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    public void afterPropertiesSet() throws ServletException {
        excludeURLRole = new ExcludeURLRole(authProperties.getExcludeUrls());
    }

    private void outErrorMsg(ServletResponse response, String result) throws IOException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json");
        ServletOutputStream out = response.getOutputStream();
        out.write(result.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

}