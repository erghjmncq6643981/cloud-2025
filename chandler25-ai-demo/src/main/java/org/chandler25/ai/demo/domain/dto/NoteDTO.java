package org.chandler25.ai.demo.domain.dto;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/21 14:32
 * @version 1.0.0
 * @since 21
 */
@Data
public class NoteDTO {
    /**
     * 主键KEY
     */
    @Schema(description = "笔记ID")
    private Long id;

    /**
     * 笔记内容
     */
    @Schema(description = "笔记内容")
    String content;
}