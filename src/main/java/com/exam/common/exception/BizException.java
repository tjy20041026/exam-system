package com.exam.common.exception;

import com.exam.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 用于表达「可以预期的业务失败」，比如账号密码错误、重复交卷。
 * 这类异常不是程序 bug，不需要打堆栈日志，也不该让调用方去 try-catch，
 * 而是直接抛出去交给 {@link GlobalExceptionHandler} 统一转成响应。
 * <p>
 * 与 RuntimeException 的区别在于语义：
 * 抛 BizException 表示「我知道这里会失败，且我知道失败的原因」。
 *
 * @see GlobalExceptionHandler
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码，对应 {@link ResultCode} */
    private final Integer code;

    public BizException(ResultCode resultCode) {
        // 这里不调用 super(message) 填充堆栈描述其实无关紧要，关键是保留 message
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 使用预定义状态码，但覆盖提示语（比如带上具体的字段名或数值） */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 业务异常不需要堆栈信息。
     * <p>
     * 它是一个「预期内的流程分支」，不是错误现场。
     * 覆写此方法可以避免每次抛出都采集堆栈——采集堆栈是很昂贵的操作，
     * 在异常用于控制流程的场景下会显著拖慢性能。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
