package com.exam.controller;

import com.exam.annotation.RequiresRole;
import com.exam.common.PageResult;
import com.exam.common.Result;
import com.exam.dto.QuestionCreateDTO;
import com.exam.dto.QuestionQueryDTO;
import com.exam.dto.QuestionUpdateDTO;
import com.exam.enums.UserRole;
import com.exam.service.QuestionService;
import com.exam.vo.QuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
 * 题库管理接口。仅教师和管理员可访问。
 */
@Tag(name = "02-题库管理", description = "题目的增删改查。仅教师和管理员可访问")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
@RequiresRole({UserRole.TEACHER, UserRole.ADMIN})
@SecurityRequirement(name = "Authorization")
public class QuestionController {

    private final QuestionService questionService;

    @Operation(summary = "录入题目",
            description = """
                    按题型自动校验完整性：
                    - SINGLE 单选：答案必须是某一个选项的 key
                    - MULTIPLE 多选：答案至少 2 个选项，用逗号分隔，如 "A,C"
                    - JUDGE 判断：答案只能是「对」或「错」，无需选项
                    - ESSAY 简答：不能带选项
                    """)
    @PostMapping
    public Result<QuestionVO> create(@Valid @RequestBody QuestionCreateDTO dto) {
        return Result.success("录入成功", questionService.create(dto));
    }

    @Operation(summary = "修改题目", description = "只传需要修改的字段。会基于修改后的完整状态重新校验")
    @PutMapping("/{id}")
    public Result<QuestionVO> update(
            @Parameter(description = "题目 ID") @PathVariable Long id,
            @Valid @RequestBody QuestionUpdateDTO dto) {
        return Result.success("修改成功", questionService.update(id, dto));
    }

    @Operation(summary = "删除题目", description = "已被试卷引用的题目会被拒绝删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "题目 ID") @PathVariable Long id) {
        questionService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "查询题目详情", description = "含标准答案，仅供教师使用")
    @GetMapping("/{id}")
    public Result<QuestionVO> getById(@Parameter(description = "题目 ID") @PathVariable Long id) {
        return Result.success(questionService.getById(id));
    }

    @Operation(summary = "分页查询题库", description = "支持按关键字、题型、难度、出题人筛选")
    @GetMapping
    public Result<PageResult<QuestionVO>> page(@Valid QuestionQueryDTO query) {
        return Result.success(questionService.page(query));
    }
}
