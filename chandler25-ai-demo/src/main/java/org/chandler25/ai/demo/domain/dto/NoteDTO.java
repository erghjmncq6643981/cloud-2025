package org.chandler25.ai.demo.domain.dto;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(title = "笔记对象", description = "笔记对象")
public class NoteDTO {
    /**
     * 主键KEY
     */
    @Schema(description = "笔记ID，ID为空表示新增，ID存在表示修改")
    private Long id;

    /**
     * 笔记内容
     */
    @Schema(description = "笔记内容")
    @NotBlank(message = "笔记内容 不可为空")
    String content;
}