package org.chandler25.ai.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/19 13:39
 * @version 1.0.0
 * @since 21
 */
@Data
@Schema(title = "注册用户DTO", description = "注册用户DTO")
public class RegisterUserDTO {
    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String nickName;

    /**
     * 登陆名
     */
    @Schema(description = "登陆名")
    private String loginName;

    /**
     * 手机号码
     */
    @Schema(description = "手机号码")
    private String phoneNumber;

    /**
     * 密码
     */
    @Schema(description = "密码")
    @NotBlank(message = "密码 不可为空")
    private String loginPassword;
}