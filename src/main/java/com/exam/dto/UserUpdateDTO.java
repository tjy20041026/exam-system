package com.exam.dto;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户的入参。
 * <p>
 * <b>刻意不包含 username 和 password</b>：
 * <ul>
 *   <li>账号是登录凭据，也是各种业务数据的关联键，不允许随意改</li>
 *   <li>改密码是独立且更敏感的操作，应该有单独的接口
 *       （需要校验旧密码、可能需要强制下线该用户的所有登录态）</li>
 * </ul>
 * 把"能改什么"用 DTO 明确框住，比在 Service 里写一堆 if 判断要清晰得多。
 */
@Data
@Schema(description = "更新用户请求")
public class UserUpdateDTO {

    @Schema(description = "真实姓名", example = "李四")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @Schema(description = "角色", example = "TEACHER",
            allowableValues = {"STUDENT", "TEACHER", "ADMIN"})
    private UserRole role;

    @Schema(description = "状态：1 启用，0 禁用", example = "1")
    private Integer status;
}
