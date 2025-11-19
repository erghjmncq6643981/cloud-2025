package org.chandler25.ai.demo.common;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.chandler25.ai.demo.respository.entity.User;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

import static org.springframework.http.HttpHeaders.USER_AGENT;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/16 15:06
 * @version 1.0.0
 * @since 21
 */
public class UserLoginCheckHelper {
    private final static String LOGIN_USER_SESSION="USER-SESSION";

    public static  void setUserSession(User session){
        RequestContextHolder.getRequestAttributes().setAttribute(LOGIN_USER_SESSION,session,0);
    }

    public static  User getUserSession() {
        RequestAttributes requestAttributes=RequestContextHolder.getRequestAttributes();
        if(Objects.nonNull(requestAttributes)){
            return (User)requestAttributes.getAttribute(LOGIN_USER_SESSION,0);
        }
        return null;
    }

}