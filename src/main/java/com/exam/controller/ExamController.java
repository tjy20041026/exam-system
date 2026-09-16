package com.exam.controller;

import com.exam.annotation.RequiresRole;
import com.exam.common.Result;
import com.exam.dto.SaveAnswerDTO;
import com.exam.enums.UserRole;
import com.exam.service.ExamService;
import com.exam.vo.ExamPaperVO;
import com.exam.vo.ExamRecordVO;
import com.exam.vo.ExamResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考试接口 —— 学生端。
 * <p>
 * 整类限定 STUDENT：教师要是能开考，exam_record 里就会多出一条和真考生长得一样的
 * 记录，混进成绩统计后没法区分，只能人工比对清理。所以从源头禁掉。
 */
@Tag(name = "05-考试（学生端）", description = """
        学生考试完整流程。

        **必须用学生账号登录**（如 student01 / Student@123），教师账号会被 403 拒绝。

        典型流程：
        1. POST /api/exams/start/{paperId}   开考，拿到 examRecordId 和题目
        2. PUT  /api/exams/{id}/answer       答题（每答一题调一次）
        3. GET  /api/exams/{id}              刷新/断点续考，拿回已答内容
        4. POST /api/exams/{id}/submit       交卷，系统自动判客观题
        5. GET  /api/exams/{id}/result       查成绩单

        ⚠️ 返回的题目**不含标准答案**，交卷后也不会返回 —— 防止先交卷的学生泄题给后考的。
        """)
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
@RequiresRole({UserRole.STUDENT})
@SecurityRequirement(name = "Authorization")
public class ExamController {

    private final ExamService examService;

    @Operation(summary = "开始考试", description = """
            进入考场，返回试卷内容 + 截止时间 + 剩余秒数。

            **这个接口是幂等的**：如果已经有进行中的考试，直接返回那一场（不会重新计时、
            不会新建记录）。所以刷新页面、换设备登录都可以放心再调一次。

            已交卷或已超时会返回错误提示，不会把题目再发一遍。
            """)
    @PostMapping("/start/{paperId}")
    public Result<ExamPaperVO> start(
            @Parameter(description = "试卷 ID") @PathVariable Long paperId) {
        return Result.success("已进入考场", examService.startExam(paperId));
    }

    @Operation(summary = "获取试卷（断点续考/刷新）", description = """
            答题过程中随时可调。返回题目 + 服务端计算的剩余秒数 + **已作答的内容**。

            作答状态存在服务端，关掉浏览器或换台电脑重新登录，调本接口就能恢复现场。
            """)
    @GetMapping("/{examRecordId}")
    public Result<ExamPaperVO> getPaper(
            @Parameter(description = "考试记录 ID（开考接口返回的 examRecordId）")
            @PathVariable Long examRecordId) {
        return Result.success(examService.getPaper(examRecordId));
    }

    @Operation(summary = "保存单题答案", description = """
            每答一题调一次。

            幂等接口：同一道题反复提交不会产生多条记录，只保留最后一次。

            `userAnswer` **允许为空字符串**（表示学生主动清空了这题）；
            不传或传 null 则视为"未作答"。

            多选题格式为 `A,C`（逗号分隔），判分时忽略顺序和大小写。
            超过截止时间后本接口会拒绝写入。
            """)
    @PutMapping("/{examRecordId}/answer")
    public Result<Void> saveAnswer(
            @Parameter(description = "考试记录 ID") @PathVariable Long examRecordId,
            @Valid @RequestBody SaveAnswerDTO dto) {
        examService.saveAnswer(examRecordId, dto);
        return Result.success("已保存", null);
    }

    @Operation(summary = "交卷", description = """
            交卷并自动判分。

            - 客观题（单选/多选/判断）自动判分
            - 简答题标记为待阅卷，需教师人工批阅
            - 全部是客观题时，状态直接变 `GRADED`；有简答题则为 `SUBMITTED`

            **防重复提交**：如果已经交过卷，会返回"本场考试已交卷"。
            并发请求下也只会有一个成功（靠数据库条件更新 + 行锁保证）。
            """)
    @PostMapping("/{examRecordId}/submit")
    public Result<Void> submit(
            @Parameter(description = "考试记录 ID") @PathVariable Long examRecordId) {
        examService.submit(examRecordId);
        return Result.success("交卷成功", null);
    }

    @Operation(summary = "查询成绩单", description = """
            返回各题作答明细、对错、得分。

            ⚠️ **不返回标准答案** —— 因为同一张试卷会被多个学生考、
            且考试时间窗口重叠，先交卷的学生看到答案就能泄题给后考的。

            未交卷时返回错误（不是 0 分的空成绩单）。
            `hasPendingEssay = true` 表示简答题还没批，当前分数是暂定的。
            """)
    @GetMapping("/{examRecordId}/result")
    public Result<ExamResultVO> result(
            @Parameter(description = "考试记录 ID") @PathVariable Long examRecordId) {
        return Result.success(examService.getResult(examRecordId));
    }

    @Operation(summary = "我的考试列表", description = """
            当前登录学生的所有考试记录，按开考时间倒序。

            返回 `canContinue` 字段，前端据此决定显示「继续考试」还是「查看成绩」。
            """)
    @GetMapping("/my")
    public Result<List<ExamRecordVO>> myRecords() {
        return Result.success(examService.myRecords());
    }
}
