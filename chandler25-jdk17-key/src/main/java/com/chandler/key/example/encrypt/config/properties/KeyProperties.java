package com.chandler.key.example.encrypt.config.properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 * @author 钱丁君-chandler 2025/12/15
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = KeyProperties.PREFIX)
public class KeyProperties {
    public static final String PREFIX = "encryption.key";
    private String algorithm = "DEF";
    private String key = "5Gpl5F5+PiAnpDZdKxqQ+Q==";
    // 旧密钥(用于密钥轮换过渡期)
    private String oldKey = "3t2QBbtBQ8kOGwa+H69S8A==";

}
