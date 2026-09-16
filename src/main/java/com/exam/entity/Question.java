package com.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.exam.enums.QuestionType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目实体，对应表 {@code question}。
 *
 * <h3>⚠️ autoResultMap 是必须的，漏了会静默出错</h3>
 * <p>
 * 注意类上的 {@code autoResultMap = true}。这不是可有可无的优化项，
 * <b>不加的话 {@code options} 字段会永远查出来是 null</b>。
 * <p>
 * 原因是 MyBatis 的字段类型处理器（TypeHandler）在<b>查询结果的映射</b>环节
 * 需要框架显式注册才会生效：
 * <ul>
 *   <li><b>写入</b>方向：MP 生成 INSERT/UPDATE 语句时会读实体上的
 *       {@code @TableField(typeHandler=...)}，所以能正常工作</li>
 *   <li><b>读取</b>方向：MP 走的是自己的一套结果映射（resultMap），
 *       除非 {@code autoResultMap=true} 让它为这个实体自动生成一个
 *       带 typeHandler 的 resultMap，否则那个注解就被完全忽略</li>
 * </ul>
 * 结果就是：写进去是好的（数据库里能看到 JSON 字符串），
 * 查出来是 null。而且<b>不报任何错</b> —— 只能靠肉眼比对数据库发现。
 * 这个坑几乎每个用 MP 处理 JSON 字段的人都会踩一次。
 *
 * <h3>为什么用 JSON 类型存选项，而不是单开一张 option 表</h3>
 * <p>
 * 选项是题目的<b>组成部分</b>，不是独立实体：它没有自己的生命周期，
 * 脱离了题目就没有意义，也从不被单独查询或引用。为它建一张表意味着
 * 每次读题都要 JOIN，写题要维护两条 INSERT —— 付出的复杂度换不到任何好处。
 * <p>
 * 反过来，如果需求是「统计某个选项被选了多少次」这种需要跨题目聚合的场景，
 * 那就该拆表了。判断依据是<b>访问模式</b>，不是"看起来规不规范"。
 */
@Data
// autoResultMap = true 必须加，原因见类注释
@TableName(value = "question", autoResultMap = true)
public class Question implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题干 */
    private String content;

    /** 题型。数据库存枚举名（SINGLE / MULTIPLE / JUDGE / ESSAY） */
    private QuestionType type;

    /**
     * 选项列表，数据库里存 JSON。
     * <p>
     * {@link JacksonTypeHandler} 负责在 Java 的 {@code List<QuestionOption>}
     * 和数据库的 JSON 字符串之间来回转换。有了它，业务代码里操作的就是
     * 一个普通的 List，完全感觉不到 JSON 的存在。
     * <p>
     * 简答题没有选项，这里为 null。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<QuestionOption> options;

    /**
     * 标准答案。格式随题型变化：
     * <ul>
     *   <li>SINGLE / JUDGE —— {@code "A"} / {@code "对"}</li>
     *   <li>MULTIPLE —— {@code "A,B,D"}，判分时拆成集合比较，忽略顺序和多余空格</li>
     *   <li>ESSAY —— 参考答案文本</li>
     * </ul>
     */
    private String answer;

    /** 建议分值。组卷时可以被覆盖，见 PaperQuestion 的说明 */
    private Integer score;

    /** 难度：1 易，2 中，3 难 */
    private Integer difficulty;

    /** 出题教师 ID */
    private Long creatorId;

    /**
     * 逻辑删除标记。
     * <p>
     * 题库表<b>可以</b>用逻辑删除，因为 {@code question} 表上没有
     * 类似 {@code uk_username} 那样的唯一约束，不存在"删了之后同名建不回来"的矛盾。
     * 而且历史考试记录会引用题目 ID，物理删除会让旧成绩单上的题目变成空白。
     * <p>
     * 对比 {@link SysUser} —— 同样是逻辑删除，能不能用得看表的具体约束。
     */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
