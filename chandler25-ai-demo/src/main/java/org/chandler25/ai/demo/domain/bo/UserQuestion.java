package org.chandler25.ai.demo.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/16 16:39
 * @version 1.0.0
 * @since 21
 */
@Data
public class UserQuestion  implements Serializable {
    @Schema(description = "问题KEY，异步时，可以使用其获取返回的结果")
    private String questionId;
    @Schema(description = "问题，用户的提问")
    private String question;
    @Schema(description = "AI的回答")
    private String answer;
}