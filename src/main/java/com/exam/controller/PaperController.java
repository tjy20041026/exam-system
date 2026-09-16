package com.exam.controller;

import com.exam.annotation.RequiresRole;
import com.exam.common.PageResult;
import com.exam.common.Result;
import com.exam.dto.PaperComposeDTO;
import com.exam.dto.PaperCreateDTO;
import com.exam.dto.PaperQueryDTO;
import com.exam.enums.UserRole;
import com.exam.service.PaperService;
import com.exam.vo.PaperDetailVO;
import com.exam.vo.PaperVO;
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
 * 试卷管理接口。仅教师和管理员可访问。
 */
@Tag(name = "03-试卷管理", description = """
        试卷的创建、组卷、发布。

        典型流程：
        1. POST /api/papers           创建试卷（此时是草稿、0 分、没有题目）
        2. PUT  /api/papers/{id}/compose  组卷（选题 + 设分值，总分自动累加）
        3. POST /api/papers/{id}/publish  发布（发布后内容冻结，学生才能开考）
        """)
@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
@RequiresRole({UserRole.TEACHER, UserRole.ADMIN})
@SecurityRequirement(name = "Authorization")
public class PaperController {

    private final PaperService paperService;

    @Operation(summary = "创建试卷", description = "创建后为草稿状态，总分 0，需再调组卷接口")
    @PostMapping
    public Result<PaperVO> create(@Valid @RequestBody PaperCreateDTO dto) {
        return Result.success("创建成功", paperService.create(dto));
    }

    @Operation(summary = "修改试卷基本信息", description = "标题、时长、时间范围。已发布的试卷会被拒绝")
    @PutMapping("/{id}")
    public Result<PaperVO> update(
            @Parameter(description = "试卷 ID") @PathVariable Long id,
            @Valid @RequestBody PaperCreateDTO dto) {
        return Result.success("修改成功", paperService.update(id, dto));
    }

    @Operation(summary = "组卷", description = """
            全量替换试卷的题目列表，并自动重算总分。

            语义是「替换」不是「追加」：本次没传的题目会被移除。
            因此这个接口是幂等的，同样的请求调多次结果一致。
            """)
    @PutMapping("/{id}/compose")
    public Result<PaperDetailVO> compose(
            @Parameter(description = "试卷 ID") @PathVariable Long id,
            @Valid @RequestBody PaperComposeDTO dto) {
        return Result.success("组卷成功", paperService.compose(id, dto));
    }

    @Operation(summary = "发布试卷", description = "要求至少有一道题。发布后试卷内容冻结，学生才能开考")
    @PostMapping("/{id}/publish")
    public Result<PaperDetailVO> publish(@Parameter(description = "试卷 ID") @PathVariable Long id) {
        return Result.success("发布成功", paperService.publish(id));
    }

    @Operation(summary = "删除试卷", description = "仅草稿状态可删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "试卷 ID") @PathVariable Long id) {
        paperService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "查询试卷详情", description = "含完整题目与答案，仅供教师使用")
    @GetMapping("/{id}")
    public Result<PaperDetailVO> getDetail(@Parameter(description = "试卷 ID") @PathVariable Long id) {
        return Result.success(paperService.getDetail(id));
    }

    @Operation(summary = "分页查询试卷列表", description = "列表不含题目，但会返回题目数量")
    @GetMapping
    public Result<PageResult<PaperVO>> page(@Valid PaperQueryDTO query) {
        return Result.success(paperService.page(query));
    }
}
