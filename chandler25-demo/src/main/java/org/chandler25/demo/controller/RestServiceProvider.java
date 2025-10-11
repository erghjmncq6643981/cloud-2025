package org.chandler25.demo.controller;

import org.chandler25.demo.entity.Person;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * restful风格的接口
 *
 * @author 钱丁君-chandler 2019/5/17下午2:00
 * @since 1.8
 */
@RestController
public class RestServiceProvider {

    @PostMapping(value = "/demo/postPerson")
    public Mono<Person> postPerson(@RequestParam("name") String name) {

        return Mono.just(Person.builder()
                .name(name)
                .age("18")
                .sex("man")
                .build());
    }

    @PostMapping(value = "/body/postPerson")
    public Mono<?> postPerson(@RequestBody Person person) {
        var result = switch (person.getName()) {
            case "chandler" -> Mono.just(person);
            default -> Mono.empty();
        };
        return result;
    }


}