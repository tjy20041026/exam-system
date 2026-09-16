package com.exam.controller;

import com.exam.annotation.RequiresRole;
import com.exam.common.PageResult;
import com.exam.common.Result;
import com.exam.dto.UserCreateDTO;
import com.exam.dto.UserQueryDTO;
import com.exam.dto.UserUpdateDTO;
import com.exam.enums.UserRole;
import com.exam.service.SysUserService;
import com.exam.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。Controller 只收参数、调 Service、包返回值，
 * 业务判断都放 Service，免得换个入口（定时任务、消息消费）就得抄一遍。
 */
@Tag(name = "01-用户管理", description = "用户的增删改查。全部接口仅管理员可访问")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
// 标在类上 = 本类所有接口都要 ADMIN；某个方法要例外就在方法上单独标 @RequiresRole 覆盖
@RequiresRole(UserRole.ADMIN)
public class UserController {

    private final SysUserService userService;

    @Operation(summary = "创建用户", description = "账号需唯一，密码会以 BCrypt 加密存储")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success("创建成功", userService.create(dto));
    }

    @Operation(summary = "更新用户", description = "只更新传入的字段，未传的字段保持原值")
    @PutMapping("/{id}")
    public Result<UserVO> update(
            @Parameter(description = "用户 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success("更新成功", userService.update(id, dto));
    }

    @Operation(summary = "删除用户", description = "物理删除。账号名随即释放，可以重新注册")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "用户 ID", example = "1") @PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> getById(
            @Parameter(description = "用户 ID", example = "1") @PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @Operation(summary = "分页查询用户", description = "所有条件均可选，不传表示不过滤")
    @GetMapping
    public Result<PageResult<UserVO>> page(@Valid UserQueryDTO query) {
        return Result.success(userService.page(query));
    }
}
