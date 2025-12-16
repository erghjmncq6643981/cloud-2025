package com.chandler.key.example.encrypt.config;

import com.chandler.key.example.encrypt.interceptor.ColumnsDecryptInterceptor;
import com.chandler.key.example.encrypt.interceptor.ColumnsEncryptInterceptor;
import com.chandler.key.example.encrypt.KeyManager;
import com.chandler.key.example.encrypt.config.properties.KeyProperties;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author 钱丁君-chandler 2025/12/16
 */
@Configuration
@EnableConfigurationProperties(KeyProperties.class)
public class KeyAutoConfiguration implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    @Bean
    public KeyManager keyManager(SqlSessionFactory sqlSessionFactory){
        KeyManager keyManager= new KeyManager();
        ColumnsDecryptInterceptor decryptInterceptor=new ColumnsDecryptInterceptor(keyManager);
        sqlSessionFactory.getConfiguration().addInterceptor(decryptInterceptor);
        ColumnsEncryptInterceptor encryptInterceptor= new ColumnsEncryptInterceptor(keyManager);
        sqlSessionFactory.getConfiguration().addInterceptor(encryptInterceptor);
        return keyManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
