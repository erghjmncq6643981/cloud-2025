package org.chandler25.ai.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.chandler25.ai.demo.common.AiFieldType;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/16 15:29
 * @version 1.0.0
 * @since 21
 */
@Data
@Schema(title = "AI上下文DTO", description = "AI上下文DTO")
public class AiContextDTO {
    /**
     * 用户ID
     */
    @Schema(description = "用户ID，不必传，会从header的token中解析")
    private Long userId;

    /**
     * 领域类型，COMMON、PROGRAMME、ENGLISH
     */
    @Schema(description = "使用场景，值为：COMMON,PROGRAMME（编程）,ENGLISH（英语），默认值为COMMON", example = "COMMON")
    private AiFieldType fieldType;

    /**
     * 用户的问题
     */
    @Schema(description = "用户的问题，不可为空", example = "我要学习这个单词：chest")
    @NotBlank(message = "用户的问题 不可为空")
    private String question;
}