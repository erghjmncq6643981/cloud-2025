package org.chandler25.ai.demo.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 14:20
 * @version 1.0.0
 * @since 21
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private List<String> excludeUrls;
}