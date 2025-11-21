package org.chandler25.ai.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 18:34
 * @version 1.0.0
 * @since 21
 */
@Data
public class LabelDTO {
    @Schema(description = "标签ID，没有时表示新增，存在时表示修改")
    private Long id;

    /**
     * 标签名称
     */
    @Schema(description = "标签名称")
    private String labelName;

    /**
     * 父标签，0表示为根节点
     */
    @Schema(description = "父标签")
    private Long labelParent;
}