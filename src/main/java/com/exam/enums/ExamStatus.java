package com.exam.enums;

import lombok.Getter;

/**
 * 考试记录状态。
 *
 * <h3>为什么 SUBMITTED 和 GRADED 要分开</h3>
 * <p>
 * 因为简答题<b>没法自动判分</b>。学生交卷那一刻，客观题已经能算出分数了，
 * 但简答题还等着老师批阅。这时候如果把状态直接标成"已完成"，
 * 学生看到的成绩就是不完整的 —— 他会以为自己只考了 20 分，
 * 而实际上还有 30 分的简答题没算。
 * <p>
 * 所以设计成两段：
 * <pre>
 *   ONGOING ──学生交卷──▶ SUBMITTED（客观题已判，简答题待阅卷）
 *                               │
 *                               └──教师阅卷──▶ GRADED（成绩最终确定）
 *
 *   ONGOING ──超时──▶ TIMEOUT（系统自动交卷，同样进入待阅卷流程）
 * </pre>
 * <p>
 * 如果一张试卷全是客观题，交卷时就能直接跳到 GRADED ——
 * 没必要让一个"没有任何待办"的记录停在中间状态，
 * 那只会让前端多写一个"什么时候该显示待阅卷"的判断。
 */
@Getter
public enum ExamStatus {

    /** 进行中：可以答题，可以恢复，可以交卷 */
    ONGOING("进行中"),

    /** 已交卷，尚有主观题待人工阅卷 */
    SUBMITTED("已交卷，待阅卷"),

    /** 已完全判卷，成绩最终确定 */
    GRADED("已判卷"),

    /** 超时被系统自动交卷 */
    TIMEOUT("超时自动交卷");

    private final String label;

    ExamStatus(String label) {
        this.label = label;
    }

    /**
     * 是否已经结束（不能再答题）。
     * <p>
     * 判断"能不能继续答题"时用这个方法，而不是逐个枚举去比 ——
     * 将来新增状态时（比如"作废"），只要在这里更新一处，
     * 所有调用点自动就对了。
     */
    public boolean isFinished() {
        return this != ONGOING;
    }

    /** 成绩是否已经完全确定 */
    public boolean isScoreFinal() {
        return this == GRADED;
    }
}
