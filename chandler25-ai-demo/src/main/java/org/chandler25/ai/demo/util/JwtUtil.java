package org.chandler25.ai.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 12:21
 * @version 1.0.0
 * @since 21
 */
public class JwtUtil {
    // 生成 Token
    public static String generateToken(String username, String password, String secret, Long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("password", password);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    // 解析 Token
    public static Claims parseToken(String token, String secret) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    // 验证 Token
    public static boolean validateToken(String token, String secret, String username) {
        try {
            String name = extractUsername(token, secret);
            return (name.equals(username)
                    && !isTokenExpired(token, secret));
        } catch (Exception e) {
            return false;
        }
    }

    // 验证 Token
    public static boolean validateToken(String token, String secret) {
        try {
            return !isTokenExpired(token, secret);
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractUsername(String token, String secret) {
        return parseToken(token, secret).getSubject();
    }

    public static boolean isTokenExpired(String token, String secret) {
        return parseToken(token, secret).getExpiration().before(new Date());
    }

    public static void main(String[] args) {
//        SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
//        String base64Secret = Base64.getEncoder().encodeToString(secretKey.getEncoded());
//        System.out.println(base64Secret);

        //生成token
        String secret = "IdXaj8cpNe+rcCphLt6uinZKh+6/OpgsQ2JMfowSQDAKb47fzyVtRXN57UVBkzxhcw/Hl+QICTMXx73y5f5ZZg==";
        String username = "chandler";
        String token = generateToken(username, "123456", secret, 60 * 60 * 1000L);
        System.out.println(token);

        System.out.println(validateToken(token, secret, username));
    }
}