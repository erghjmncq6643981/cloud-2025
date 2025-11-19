package org.chandler25.ai.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.chandler25.ai.demo.common.AiQuestionStatus;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/17 13:27
 * @version 1.0.0
 * @since 21
 */
@Data
public class ChatHistoryDTO {
    /**
     * 问题ID
     */
    @Schema(description = "问题KEY，异步时，可以使用其获取返回的结果")
    private String questionId;

    @Schema(description = "问题，用户的提问")
    private String question;
    @Schema(description = "AI的回答")
    private String answer;
    /**
     * 问题状态
     */
    @Schema(description = "问题状态，NEW-进行中,ANSWERED-已回答,SUSPENDED-中止,ERROR-发生异常")
    private AiQuestionStatus questionStatus;
}