package com.exam.dto;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户的入参。
 * <p>
 * <b>为什么不直接用 SysUser 实体接收入参？</b>
 * 因为实体包含 id、createTime、deleted 这些客户端不该决定的字段。
 * 如果直接用实体接收，客户端就能传 id 来覆盖指定记录，传 deleted 来绕过逻辑删除。
 * 用专门的 DTO 只暴露"允许客户端填的字段"，是防参数注入的第一道关卡。
 */
@Data
@Schema(description = "创建用户请求")
public class UserCreateDTO {

    @Schema(description = "登录账号，4-50 位字母数字下划线", example = "student001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 50, message = "账号长度需在 4-50 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字和下划线")
    private String username;

    @Schema(description = "密码，6-32 位", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @Schema(description = "真实姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @Schema(description = "角色", example = "STUDENT", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"STUDENT", "TEACHER", "ADMIN"})
    @NotNull(message = "角色不能为空")
    private UserRole role;
}
