package com.exam.vo;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息的出参。
 * <p>
 * <b>为什么必须有 VO，不能直接返回 SysUser 实体？</b>
 * <ol>
 *   <li><b>防止敏感字段泄露</b>：SysUser 上有 password（虽然加了 @JsonIgnore
 *       作为双保险），但依赖注解不如从类型上就杜绝 —— VO 里根本没有这个字段</li>
 *   <li><b>可附加展示字段</b>：比如这里的 roleLabel 是角色中文名，
 *       数据库里并不存在，是给前端直接显示用的</li>
 *   <li><b>解耦</b>：数据库字段改名、拆分表时，只要 VO 不变，前端就无需改动</li>
 * </ol>
 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "登录账号", example = "student001")
    private String username;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "角色代码", example = "STUDENT")
    private UserRole role;

    /** 角色的中文名，由 role 推导而来，方便前端直接显示而不必自己维护映射表 */
    @Schema(description = "角色中文名", example = "学生")
    private String roleLabel;

    @Schema(description = "状态：1 启用，0 禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
