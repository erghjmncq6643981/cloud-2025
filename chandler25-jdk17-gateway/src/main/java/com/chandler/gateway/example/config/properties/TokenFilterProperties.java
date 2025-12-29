package com.chandler.gateway.example.config.properties;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;

/**
 *
 * @author 钱丁君-chandler 2025/12/25
 */
@Data
@RefreshScope
@NoArgsConstructor
@ConfigurationProperties(prefix = TokenFilterProperties.PREFIX)
public class TokenFilterProperties {
    public static final String PREFIX = "token.filter";
    private List<String> domainNames = Lists.newArrayList();
    private List<String> excludeDomainNames = Lists.newArrayList();
}
