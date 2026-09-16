package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 试卷详情，含题目列表，仅教师可见。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "试卷详情（含题目与答案，仅供教师使用）")
public class PaperDetailVO extends PaperVO {

    @Schema(description = "题目列表，按 sortOrder 升序")
    private List<PaperQuestionVO> questions;
}
