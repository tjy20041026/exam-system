package com.exam.common.exception;

import com.exam.common.Result;
import com.exam.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。业务代码里不再写 try-catch，统一在这里转成 {@link Result}。
 * <p>
 * 未知路径的 404 必须单独接住：Spring Boot 默认把匹配不到 @RequestMapping 的请求当静态资源处理，
 * 最终抛的是 {@link NoResourceFoundException}，不接就会掉进最底下的兜底 handler，
 * 被当成程序缺陷打出一整屏 ERROR —— 客户端 URL 写错而已，属于误报。
 * <p>
 * 兜底 handler 对外只返回模糊的「系统繁忙」，异常消息里常带 SQL 片段、表名、绝对路径。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，打 WARN 不打堆栈 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** @Valid 校验失败（作用于 @RequestBody 对象的字段上），把字段错误拼成人话返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /** 表单绑定校验失败，与上面同理，只是触发场景不同 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn(message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /** 请求体不是合法 JSON，或类型对不上（比如给 int 字段传了 "abc"） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, "请求体格式错误，请检查 JSON 是否合法、字段类型是否匹配");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String message = "请求方法不支持: " + e.getMethod() + "，支持的方法是: " + e.getSupportedHttpMethods();
        log.warn(message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /** 接口不存在。Spring Boot 3.2+ 的 404 走的是这个，不是 NoHandlerFoundException */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        String message = "接口不存在: " + e.getResourcePath();
        log.warn(message);
        return Result.error(ResultCode.NOT_FOUND, message);
    }

    /**
     * 旧版 Spring 的 404 出口，没开 throw-exception-if-no-handler-found 所以不会触发，
     * 留着只是成本为零。真正生效的是上面那个。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("接口不存在: {}", e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /** 兜底：走到这里说明是程序缺陷。日志带堆栈，对外只返回模糊提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常（未被预期处理，属于程序缺陷，需排查）", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
