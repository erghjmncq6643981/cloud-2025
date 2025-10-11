/*
 * chandler25-elasticsearch
 * 2025/8/15 21:56
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.elasticsearch.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import org.chandler25.elasticsearch.entity.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/15 21:56
 * @version 1.0.0
 * @since 1.8
 */
@RestController
public class ElasticsearchProvider {
    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @GetMapping(value = "/es/getPerson")
    public Person getDocument(@RequestParam String id){
        // 查询文档
        try {
            GetResponse<Person> getResponse = elasticsearchClient.get(g -> g
                    .index("person")
                    .id(id), Person.class
            );
            return getResponse.source();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}