package org.chandler25.ai.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 18:34
 * @version 1.0.0
 * @since 21
 */
@Data
@Schema(description = "标签")
public class LabelDTO {
    @Schema(description = "标签ID，没有时表示新增，存在时表示修改")
    private Long id;

    /**
     * 标签名称
     */
    @Schema(description = "标签名称")
    @NotBlank(message = "标签名称 不可为空")
    private String labelName;

    /**
     * 父标签，0表示为根节点
     */
    @Schema(description = "父标签，没有父标签传0",example = "0")
    @NotNull(message = "父标签 不可为空")
    private Long labelParent;
}