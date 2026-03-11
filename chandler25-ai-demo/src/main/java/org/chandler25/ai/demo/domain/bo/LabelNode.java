package org.chandler25.ai.demo.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/21 21:15
 * @version 1.0.0
 * @since 21
 */
@Data
@Schema(description = "标签笔记绑定对象")
public class LabelNode {
    @Schema(description = "标签ID")
    private Long id;

    /**
     * 标签名称
     */
    @Schema(description = "标签名称")
    private String labelName;

    /**
     * 子标签
     */
    @Schema(description = "子标签集合")
    private List<LabelNode> nodes;
}