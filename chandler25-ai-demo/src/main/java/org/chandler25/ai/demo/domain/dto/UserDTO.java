package org.chandler25.ai.demo.domain.dto;

import com.mybatisflex.annotation.Column;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.chandler25.ai.demo.common.UserStatus;
import org.chandler25.ai.demo.common.UserType;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 11:40
 * @version 1.0.0
 * @since 21
 */
@Data
@Schema(title = "用户DTO", description = "用户DTO")
public class UserDTO {
    @Schema(description = "用户ID，新增时为空，修改时必传")
    private Long id;

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    @NotBlank(message = "昵称不可为空")
    private String nickName;

    /**
     * 登陆名
     */
    @Schema(description = "登陆名")
    @NotBlank(message = "登陆名 不可为空")
    private String loginName;

    /**
     * 密码
     */
    @Schema(description = "密码")
    @NotBlank(message = "密码 不可为空")
    private String loginPassword;

    /**
     * 手机号码
     */
    @Schema(description = "手机号码")
    private String phoneNumber;

    /**
     * 用户类型
     */
    @Schema(description = "用户类型，游客：VISITOR；用户：USER； 管理员：ADMINISTRATOR")
    @NotNull(message = "用户类型 不可为空")
    private UserType userType;

    /**
     * 用户状态，ENABLED、DISABLED
     */
    @Schema(description = "用户状态，值为为ENABLED、DISABLED；默认值为为ENABLED，可空")
    private UserStatus userStatus;
}