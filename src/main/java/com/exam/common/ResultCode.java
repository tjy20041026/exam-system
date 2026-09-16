package com.exam.common;

import lombok.Getter;

/**
 * 统一状态码。
 * <p>
 * 集中定义而非散落各处的魔法数字，改一处即可全局生效。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    // ---------- 客户端错误 ----------
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限执行此操作"),
    NOT_FOUND(404, "请求的资源不存在"),

    /** 冲突。用于重复交卷、重复开考这类"状态不允许"的场景 */
    CONFLICT(409, "操作冲突，请勿重复提交"),

    // ---------- 服务端错误 ----------
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),

    // ---------- 业务错误（考试系统专属） ----------
    EXAM_NOT_STARTED(1001, "考试尚未开始"),
    EXAM_ENDED(1002, "考试已结束"),
    /**
     * 考试超时。
     * <p>
     * 提示语刻意<b>不</b>写"系统已自动交卷" —— 因为这句话在超时刚发生时是不成立的：
     * 定时任务（Day 6 才实现）可能还没扫到这条记录，学生的卷子实际还挂在 ONGOING 上。
     * <p>
     * 让他以为"已经交卷了"，他就不会再去查成绩，也就不知道自己其实还没交上 ——
     * <b>错误提示说的话必须是当前时刻为真的</b>，
     * 哪怕下一毫秒它就会变成真的。
     */
    EXAM_TIMEOUT(1003, "考试时间已到，无法继续作答"),
    EXAM_ALREADY_SUBMITTED(1004, "本场考试已交卷，无法重复提交"),
    EXAM_NOT_OWNED(1005, "无权操作他人的考试记录"),
    PAPER_NOT_PUBLISHED(1006, "试卷未发布，无法参加考试"),
    EXAM_RECORD_NOT_FOUND(1007, "考试记录不存在"),
    QUESTION_NOT_IN_PAPER(1008, "该题目不属于本场考试"),
    EXAM_NOT_SUBMITTED(1009, "本场考试尚未交卷，暂无成绩"),

    USER_NOT_FOUND(2001, "用户不存在"),
    PASSWORD_ERROR(2002, "账号或密码错误"),
    USER_DISABLED(2003, "账号已被禁用"),
    USERNAME_EXISTS(2004, "账号已存在"),

    // ---------- 题库（3xxx）----------
    QUESTION_NOT_FOUND(3001, "题目不存在"),
    QUESTION_IN_USE(3002, "题目已被试卷引用，无法删除"),
    QUESTION_ANSWER_INVALID(3003, "答案与选项不匹配"),

    // ---------- 试卷（4xxx）----------
    PAPER_NOT_FOUND(4001, "试卷不存在"),
    PAPER_NOT_EDITABLE(4002, "试卷已发布，内容已冻结，不能再修改"),
    PAPER_EMPTY(4003, "试卷还没有题目，无法发布"),
    PAPER_TIME_INVALID(4004, "开考时间必须早于截止时间"),
    PAPER_QUESTION_DUPLICATE(4005, "同一道题不能在一张试卷里重复出现");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
