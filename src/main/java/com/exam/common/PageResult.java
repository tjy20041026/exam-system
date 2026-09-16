package com.exam.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 分页结果封装。不直接返回 IPage，免得把 orders、optimizeCountSql 这些内部字段透出去。 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "当前页数据")
    private List<T> records;

    @Schema(description = "总记录数", example = "137")
    private Long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private Long current;

    @Schema(description = "每页条数", example = "10")
    private Long size;

    @Schema(description = "总页数", example = "14")
    private Long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, Long total, Long current, Long size) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.current = current;
        this.size = size;
        // size 有可能是 0，不能直接当除数
        this.pages = (size == null || size == 0) ? 0L : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 构造的同时把 entity 逐条转成 VO */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        List<T> voList = page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageResult<>(voList, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 列表由调用方自己组装，这里只借用分页对象的 total / current / size。
     * <p>
     * 用在字段需要跨记录批量加工的场景，比如试卷列表要显示「每张试卷有几道题」，
     * 得先 GROUP BY 统计一遍再填回去，上面那个转换函数一次只能看到一条记录，做不了。
     */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
