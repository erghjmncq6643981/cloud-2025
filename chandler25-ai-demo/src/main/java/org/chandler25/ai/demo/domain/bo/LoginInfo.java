package org.chandler25.ai.demo.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/17 15:44
 * @version 1.0.0
 * @since 21
 */
@Data
public class LoginInfo {
    /**
     * 登陆名
     */
    @Schema(description = "登陆名",example = "chandler")
    @NotBlank(message = "登陆名 不可为空")
    private String loginName;

    /**
     * 密码
     */
    @Schema(description = "密码",example = "123456")
    @NotBlank(message = "密码 不可为空")
    private String loginPassword;
}