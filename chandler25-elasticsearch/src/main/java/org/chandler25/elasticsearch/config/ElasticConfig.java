package org.chandler25.elasticsearch.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "es")
public class ElasticConfig {

    @Value("${es.order.data.path}")
    private String dataPath;
    @Value("${es.order.index}")
    private String orderIndex;
    
    @Value("${spring.data.elasticsearch.cluster-nodes}")
    private String clusterNodes;
    
    @Value("${spring.data.elasticsearch.schema}")
    private String schema;
    
    @Value("${spring.data.elasticsearch.username:}")
    private String username;
    
    @Value("${spring.data.elasticsearch.password:}")
    private String password;

}
