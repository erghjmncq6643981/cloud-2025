
package com.chandler.key.example;

import com.chandler.key.example.encrypt.interceptor.ColumnsDecryptInterceptor;
import com.chandler.key.example.encrypt.interceptor.ColumnsEncryptInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 模板工程
 *
 * @version 1.0
 * @author 钱丁君-chandler 4/2/21 10:05 PM
 * @since 1.8
 */
@MapperScan("com.chandler.key.example.domain.mapper")
@EnableDiscoveryClient
@SpringBootApplication
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 注册MyBatis拦截器
     * @param sqlSessionFactory
     * @return
     */
    @Bean
    public String myInterceptor(SqlSessionFactory sqlSessionFactory){

        sqlSessionFactory.getConfiguration().addInterceptor(parameterIntercept());

        sqlSessionFactory.getConfiguration().addInterceptor(resultSetIntercept());

        return "myInterceptor";
    }

    public ColumnsDecryptInterceptor parameterIntercept(){
        return applicationContext.getAutowireCapableBeanFactory().createBean(ColumnsDecryptInterceptor.class);
    }

    public ColumnsEncryptInterceptor resultSetIntercept(){
        return applicationContext.getAutowireCapableBeanFactory().createBean(ColumnsEncryptInterceptor.class);
    }
}
