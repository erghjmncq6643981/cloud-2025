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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.chandler25.ai.demo.common.AiFieldType;
import org.chandler25.ai.demo.common.TemplateStatus;
import org.chandler25.ai.demo.domain.bo.UserQuestion;
import org.chandler25.ai.demo.domain.dto.AiContextDTO;
import org.chandler25.ai.demo.domain.dto.AiTemplateDTO;
import org.chandler25.ai.demo.respository.entity.AiGeneratedDataHistory;
import org.chandler25.ai.demo.service.AiService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/9/29 16:10
 * @version 1.0.0
 * @since 1.8
 */
@Tag(name = "AI接口", description = "AI接口")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {
    private final AiService aiService;

    @Operation(summary = "问问题", description = "isSync=true,表示同步，isSync=false，表示异步")
    @PostMapping("/chat")
    public UserQuestion chat(@Valid @RequestBody AiContextDTO aiContext,
                             @RequestParam(value = "isSync") Boolean isSync) {
        return aiService.chat(aiContext,isSync);
    }

    @Operation(summary = "AI问答历史", description = "保留最近5条问答记录）")
    @GetMapping("/chat/history")
    public List<AiGeneratedDataHistory> chatHistory() {
        return aiService.chatHistories();
    }

    @Operation(summary = "查询异步结果", description = "根据questionId查询异步结果）")
    @GetMapping("/chat/async/answer")
    public AiGeneratedDataHistory queryHistoryByKey(String questionId ){
        AiGeneratedDataHistory history= aiService.queryHistoryByKey(questionId);
        return history;
    }

    @Operation(summary = "新增or修改AI问答模版", description = "新增or修改AI问答模版）")
    @PostMapping("/ai/template")
    public void addOrUpdateAiTemplate(@RequestBody AiTemplateDTO aiTemplateDTO){
        if(Objects.isNull(aiTemplateDTO.getFieldType())){
            aiTemplateDTO.setFieldType(AiFieldType.COMMON);
        }
        if(Objects.isNull(aiTemplateDTO.getUserStatus())){
            aiTemplateDTO.setUserStatus(TemplateStatus.ENABLED);
        }
        aiService.addOrUpdate(aiTemplateDTO);
    }
}