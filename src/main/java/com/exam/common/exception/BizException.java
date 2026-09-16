package com.exam.common.exception;

import com.exam.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常。表达「可以预期的业务失败」，不是程序 bug，直接抛出去
 * 交给 {@link GlobalExceptionHandler} 转成响应即可。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 业务异常是预期内的流程分支，不是错误现场，没必要采集堆栈 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
