package com.exam.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 统一响应体。所有接口一律返回这个结构，前端判断一次 code 就能区分成功与失败。 */
@Data
@Schema(description = "统一响应体")
public class Result<T> implements Serializable {

    @Schema(description = "状态码，200 表示成功", example = "200")
    private Integer code;

    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    @Schema(description = "业务数据，失败时为 null")
    private T data;

    @Schema(description = "服务端时间戳（毫秒）")
    private Long timestamp;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功，不带数据 */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 成功，自定义提示语 */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /** 失败，使用预定义状态码 */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败，使用预定义状态码但覆盖提示语 */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}
