package com.chandler.gateway.example.feignclient;

import feign.codec.Decoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 *
 * @author 钱丁君-chandler 2025/12/10
 */
@Configuration
public class FeignConfig {

    @Bean
    public Decoder decoder(){
        return new ResponseEntityDecoder(new SpringDecoder(feignHttpMessageConverter()));
    }
    private ObjectFactory<HttpMessageConverters> feignHttpMessageConverter(){
        HttpMessageConverters httpMessageConverters=new HttpMessageConverters
                (new MappingJackson2HttpMessageConverter());
        return ()->httpMessageConverters;
    }

}
