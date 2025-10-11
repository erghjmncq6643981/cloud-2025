/*
 * chandler25-ai-demo
 * 2025/9/29 16:10
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.ai.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/9/29 16:10
 * @version 1.0.0
 * @since 1.8
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        return chatClient.prompt()
                .user(msg)
                .call()
                .content();
    }
    
    /**
     * @param 
     * @return 
     * {@Description} 提示词模版
     * {@Author} chandler
     * {@create} 2025/9/29 18:15
     */
    @GetMapping("/prompt/chat")
    public String chatParam(@RequestParam String name) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Tell me the names of 5 movies whose soundtrack was composed by {composer}")
                        .param("composer", name)) //John Williams
                .call()
                .content();
    }

    @GetMapping("/flux/chat")
    public Flux<String> fluxChat(@RequestParam String msg) {
        return chatClient.prompt()
                .user(msg)
                .stream()
                .content();
    }
}