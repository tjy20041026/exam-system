package com.exam.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果封装。
 * <p>
 * 不直接把 MyBatis-Plus 的 {@code IPage} 返回给前端，原因有三：
 * <ol>
 *   <li>IPage 里含 orders、optimizeCountSql 等前端完全用不到的内部字段，
 *       暴露出去既冗余又可能泄露实现细节</li>
 *   <li>数据库分页与接口契约解耦 —— 将来换 ORM 不用改前端</li>
 *   <li>可以顺便把 entity 转成 VO，避免把数据库字段直接透出去</li>
 * </ol>
 *
 * @param <T> 列表元素的类型（通常是 VO）
 */
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
        // 计算总页数。注意 size 为 0 时不能做除数，否则抛 ArithmeticException
        this.pages = (size == null || size == 0) ? 0L : (total + size - 1) / size;
    }

    /** 由 MyBatis-Plus 的分页对象直接构造（元素类型即 VO 类型） */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 由 MyBatis-Plus 的分页对象构造，同时把 entity 逐条转换成 VO。
     * <p>
     * 这样可以避免"查完再手动 new 一个 PageResult 然后循环转换"的重复代码，
     * 也不用引入 BeanUtils 之类的反射工具（反射慢且字段名写错时不会报错）。
     *
     * @param page      数据库分页结果
     * @param converter entity -> VO 的转换函数，通常传 {@code this::toVO}
     */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        List<T> voList = page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageResult<>(voList, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 记录已加工好时使用：只借用分页对象的<b>元信息</b>（总数、页码、页大小），
     * 列表内容用调用方自己给的。
     *
     * <p><b>什么时候需要它？</b>当 VO 里的某些字段无法由单条记录算出来，
     * 需要额外做一次批量查询时。
     * <p>
     * 具体例子见 {@code PaperServiceImpl.page()}：试卷列表要显示"每张试卷有几道题"，
     * 而这个数字存在 paper_question 表里，得用一条 {@code GROUP BY} 查询
     * 一次性统计出来，再填回每条记录。
     * <p>
     * 这种情况下，{@code of(page, converter)} 那个重载就不够用了 ——
     * 它的转换函数一次只能看到一条记录，拿不到批量统计的结果。
     * <p>
     * 所以两个重载的分工是：
     * <ul>
     *   <li>字段只依赖单条记录 → 用 {@code of(page, converter)}，最省事</li>
     *   <li>字段需要跨记录批量加工 → 先自己组装列表，再用这个重载补上分页元信息</li>
     * </ul>
     *
     * @param page    数据库分页对象，仅用于取 total / current / size
     * @param records 已经转换并加工完毕的当前页数据
     */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
