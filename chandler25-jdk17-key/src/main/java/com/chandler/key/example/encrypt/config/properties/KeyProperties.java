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
    private String algorithm;

    private String idCardKey;

    private String phoneKey;

    private String bankCardKey;

}
