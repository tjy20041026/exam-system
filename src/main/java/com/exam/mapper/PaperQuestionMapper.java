package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.dto.PaperQuestionCount;
import com.exam.entity.PaperQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 试卷题目关联 Mapper。
 */
@Mapper
public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {

    /**
     * 批量插入关联行。
     *
     * <h3>为什么不循环调用 BaseMapper.insert()</h3>
     * <p>
     * 循环插入 N 条就是 N 次网络往返 + N 条 SQL 语句。组卷一次可能有几十道题，
     * 意味着几十次往返，而每次往返都要经历「发送 → 数据库解析 SQL → 执行 → 返回」。
     * <p>
     * 下面这条语句把所有行拼成<b>一条</b> INSERT 发过去，
     * 数据库只需要解析一次 SQL、执行一次。在几十条的量级上，
     * 差距通常在几倍到十几倍之间。
     *
     * <h3>MyBatis 动态 SQL 说明</h3>
     * <ul>
     *   <li>{@code <script>} 标签让注解里也能用 XML 的动态标签。
     *       不用它的话就得单独写一个 XML 映射文件</li>
     *   <li>{@code <foreach>} 负责把集合展开成 {@code (?,?),(?,?),(?,?)} 的形式。
     *       {@code collection='list'} 是约定的名字 ——
     *       参数是 List 时 MyBatis 默认把它命名为 {@code list}
     *       （配 {@code @Param("list")} 是为了让名字显式、不依赖约定）</li>
     *   <li>{@code separator=','} 在每轮之间插逗号，<b>最后一条后面不加</b> ——
     *       这正是手写字符串拼接最容易出错的地方</li>
     * </ul>
     *
     * <h3>安全性</h3>
     * <p>
     * 值全部用 <b>{@code #{}}</b> 而不是 {@code ${}}。
     * {@code #{}} 会生成 PreparedStatement 的占位符 {@code ?}，参数走二进制协议传输，
     * <b>天然免疫 SQL 注入</b>；而 {@code ${}} 是直接文本替换，
     * 用户传什么就拼进 SQL 里，是注入漏洞的直接来源。
     * <p>
     * 规则很简单：<b>值用 {@code #{}}，只有在需要拼接表名/列名这类 SQL 结构时才用 {@code ${}}，
     * 且那些值绝不能来自用户输入。</b>
     *
     * @param list 待插入的关联行，调用方需保证非空 —— 空集合会拼出
     *             {@code INSERT INTO ... VALUES}（后面什么都没有）这种语法错误的 SQL
     * @return 实际插入的行数
     */
    @Insert("<script>" +
            "INSERT INTO paper_question (paper_id, question_id, score, sort_order) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.paperId}, #{item.questionId}, #{item.score}, #{item.sortOrder})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<PaperQuestion> list);

    /**
     * 批量统计多张试卷的题目数量。
     *
     * <h3>这条查询解决的是典型的 N+1 问题</h3>
     * <p>
     * 试卷列表页要显示"每张试卷有多少道题"。最直觉的写法是：
     * <pre>
     *   for (Paper p : papers) {                       // 1 次查询拿到 N 张试卷
     *       int count = countByPaperId(p.getId());     // 循环里又查 N 次！
     *   }
     * </pre>
     * 一共发出 <b>1 + N</b> 次查询。列表分页 10 条就是 11 次数据库往返，
     * 100 条就是 101 次。这就是 N+1 查询问题 —— 它<b>不会报错</b>，
     * 本地数据少时也感觉不到慢，往往等到压力测试时才暴露。
     * <p>
     * 正确做法是用一次带 {@code GROUP BY} 的聚合查询把 N 个结果一次拿回来，
     * 总数固定为 <b>2 次查询</b>，与列表长度无关。
     *
     * <h3>为什么用 IN 而不是 JOIN</h3>
     * <p>
     * 也能写成 {@code paper LEFT JOIN paper_question ... GROUP BY paper.id}，
     * 一次查询搞定。但那样会把 paper 的所有列都查一遍（列表页并不需要），
     * 而且 JOIN + GROUP BY 在数据量大时对执行计划的要求更高。
     * <p>
     * 分成两条简单查询，各自都能走索引，结果在应用层用 Map 合并 ——
     * 这是"用一点点应用层代码换数据库简单性"的常见取舍。
     *
     * @param paperIds 试卷 ID 集合，调用方需保证非空（空集合会拼出语法错误的 SQL）
     */
    @Select("<script>" +
            "SELECT paper_id AS paperId, COUNT(*) AS questionCount " +
            "FROM paper_question " +
            "WHERE paper_id IN " +
            "<foreach collection='paperIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY paper_id" +
            "</script>")
    List<PaperQuestionCount> countByPaperIds(@Param("paperIds") List<Long> paperIds);
}
