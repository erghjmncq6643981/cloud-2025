package org.chandler25.ai.demo.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/21 14:40
 * @version 1.0.0
 * @since 21
 */
@Data
public class NoteData {
    @Schema(description = "笔记内容")
    private String content;
}