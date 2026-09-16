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
 * 全局异常处理器。
 * <p>
 * 有了它，Controller / Service 里就不用再写 try-catch —— 直接抛，
 * 这里统一兜住并转成 {@link Result} 返回。
 * <p>
 * 处理原则：
 * <ul>
 *   <li><b>可预期的业务异常</b>：打 WARN 日志（不打堆栈），返回具体原因给前端</li>
 *   <li><b>参数校验失败</b>：把「哪个字段错了、错在哪」拼成人话返回，方便前端直接弹提示</li>
 *   <li><b>路径 / 方法不存在</b>：属于客户端把 URL 写错了，打 WARN 就够了 ——
 *       <b>绝不能落进"未知异常"那一档</b>，否则线上会被 404 扫描刷满假故障</li>
 *   <li><b>未知异常</b>：打 ERROR 日志（带完整堆栈，供自己排查），
 *       但返回给前端的是模糊的「系统繁忙」—— 绝不能把 e.getMessage() 透出去</li>
 * </ul>
 *
 * <b>为什么未知异常不能返回真实错误信息？</b>
 * 因为异常消息里常含有 SQL 片段、表名、类名、文件绝对路径等内部信息。
 * 攻击者可以据此推断系统结构（比如从 SQL 报错反推出表名和字段名），
 * 属于典型的「信息泄露」漏洞。日志里记全，对外只说「系统繁忙」。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    /**
     * 业务异常：可预期，用 WARN 级别记录。
     * 不打堆栈是因为 BizException 已经覆写了 fillInStackTrace，本就没有堆栈。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验相关 ====================

    /**
     * @Valid 校验失败（作用于 @RequestBody 对象的字段上）。
     * 把每个字段的错误拼成「字段名: 错误原因」，多个用分号隔开。
     */
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

    /** 缺少必填的请求参数，比如 @RequestParam 没有传 */
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

    // ==================== 路由相关 ====================

    /** 请求方法不对，比如该用 POST 却用了 GET */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String message = "请求方法不支持: " + e.getMethod() + "，支持的方法是: " + e.getSupportedHttpMethods();
        log.warn(message);
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 接口不存在（Spring Boot 3.2+ 的实际情况）。
     *
     * <h3>这里有个踩过的坑，值得说明</h3>
     * <p>
     * 直觉上"接口不存在"应该由 {@link NoHandlerFoundException} 处理，
     * 所以下面对应的那个 handler 看起来完全正确 —— 但<b>它一次也不会被触发</b>。
     * <p>
     * 原因是 Spring Boot 的默认行为：请求进来若匹配不到任何
     * {@code @RequestMapping}，DispatcherServlet 不会抛
     * {@code NoHandlerFoundException}（那需要显式开启
     * {@code spring.mvc.throw-exception-if-no-handler-found=true}），
     * 而是把它当成"找静态资源"继续往下走，最后由
     * {@code ResourceHttpRequestHandler} 抛出 {@link NoResourceFoundException}。
     * <p>
     * 后果是：不捕获它的话，一个普通的 404 会掉进最底下的兜底 handler，
     * 被判定为"程序缺陷"，打出一整屏 {@code ERROR} 堆栈。
     * <b>这属于误报</b> —— 客户端把 URL 写错了是它的问题，
     * 不是服务端的缺陷。这种日志的危害是会淹没真正的故障，
     * 而且看日志的人会被误导去排查一个根本不存在的问题。
     * <p>
     * 这个 bug 是我在回归测试时发现的：我用变量拼 URL，
     * 变量为空导致请求路径变成了 {@code /api/exams//answer}，
     * 本来只是想验证参数错误，却在日志里看到了"程序缺陷"级别的报警。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        String message = "接口不存在: " + e.getResourcePath();
        log.warn(message);
        return Result.error(ResultCode.NOT_FOUND, message);
    }

    /**
     * 接口不存在（旧版 Spring 的行为，本项目当前不会触发）。
     * <p>
     * 保留它是因为它成本为零，而且一旦有人把
     * {@code spring.mvc.throw-exception-if-no-handler-found} 打开，
     * 这个 handler 立刻就变成生效的那一个。
     * <p>
     * <b>但必须写明它现在不生效</b> —— 否则下一个读代码的人
     * （包括几个月后的自己）会以为 404 已经处理好了，
     * 从而不会去发现真正生效的其实是上面那个。
     * 留着一段"看起来对但从不执行"的代码而不加说明，
     * 比没有这段代码更危险。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("接口不存在: {}", e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    // ==================== 兜底 ====================

    /**
     * 未捕获的异常，说明是程序缺陷。
     * 日志打全（带堆栈，供你排查），但对外只返回模糊提示。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常（未被预期处理，属于程序缺陷，需排查）", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
