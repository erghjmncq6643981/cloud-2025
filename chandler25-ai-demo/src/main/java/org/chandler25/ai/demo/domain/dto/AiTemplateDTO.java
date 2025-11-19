package org.chandler25.ai.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.chandler25.ai.demo.common.AiFieldType;
import org.chandler25.ai.demo.common.TemplateStatus;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/16 16:08
 * @version 1.0.0
 * @since 21
 */
@Data
public class AiTemplateDTO {
    @Schema(description = "模板ID，新增时为空，修改时必传")
    private Long id;
    /**
     * 领域类型，COMMON、PROGRAMME、ENGLISH
     */
    @Schema(description = "使用场景，值为：COMMON,PROGRAMME（编程）,ENGLISH（英语），默认值为COMMON")
    private AiFieldType fieldType;

    /**
     * AI请求模版
     */
    @Schema(description = "AI模型问题模板，{question}是用户问题的占位符", example = "问题：{question} \\d 我是一个编程初学者，先判断是不是一个编程领域的问题。返回的内容需要包含涉及的概念，代码示例，实现原理、应用场景、面试相关问题")
    @NotBlank(message = "AI模型问题模板 不可为空")
    private String template;

    /**
     * 模版状态，ENABLED、DISABLED
     */
    @Schema(description = "模版状态，值为为ENABLED、DISABLED；默认值为为ENABLED，可空")
    private TemplateStatus userStatus;
}