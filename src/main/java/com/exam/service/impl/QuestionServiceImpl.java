package com.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.PageResult;
import com.exam.common.ResultCode;
import com.exam.common.UserContext;
import com.exam.common.exception.BizException;
import com.exam.converter.QuestionConverter;
import com.exam.dto.QuestionCreateDTO;
import com.exam.dto.QuestionQueryDTO;
import com.exam.dto.QuestionUpdateDTO;
import com.exam.entity.Paper;
import com.exam.entity.PaperQuestion;
import com.exam.entity.Question;
import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import com.exam.mapper.PaperMapper;
import com.exam.mapper.PaperQuestionMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.service.QuestionService;
import com.exam.vo.QuestionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库服务实现。
 *
 * <h3>本类的核心：按题型校验答案</h3>
 * <p>
 * 题目的"完整性"没有统一的定义 —— 单选题要有一个合法选项作答案，
 * 多选题要有至少两个，判断题的答案只能是"对/错"，简答题则不该有选项。
 * 这类<b>随类型变化的规则</b>是最容易出 bug 的地方，
 * 所以这里把它集中在一个方法里（{@link #validateByType}），
 * 而不是散落在 create / update 各处。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperMapper paperMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO create(QuestionCreateDTO dto) {
        validateByType(dto.getType(), dto.getOptions(), dto.getAnswer());

        Question question = new Question();
        question.setContent(dto.getContent());
        question.setType(dto.getType());
        question.setOptions(dto.getOptions());
        question.setAnswer(dto.getAnswer());
        question.setScore(dto.getScore());
        question.setDifficulty(dto.getDifficulty());
        // 出题人从登录上下文取，【不接受客户端传入】。
        // 如果让前端传 creatorId，任何人都能把题目挂到别人名下 —
        // 这类"身份相关的字段必须由服务端根据登录态推断"是一条铁律，
        // 凡是客户端能自己声明的身份字段，都等于没有身份校验
        question.setCreatorId(UserContext.getUserId());

        questionMapper.insert(question);

        log.info("录入题目成功: id={}, type={}, creatorId={}",
                question.getId(), question.getType(), question.getCreatorId());
        return QuestionConverter.toVO(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO update(Long id, QuestionUpdateDTO dto) {
        Question existing = questionMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.QUESTION_NOT_FOUND);
        }

        // 【关键】校验必须基于"合并后的最终状态"，而不是只看本次改动。
        //
        // 反例：原题是单选题，选项为 A/B/C，答案 "A"。
        // 现在只把 type 改成 MULTIPLE，其余不动。
        // 如果只校验 dto 里的字段，会发现 answer 是 null（没传），
        // 于是跳过校验 —— 结果库里出现一道"多选题但只有一个答案"的脏数据。
        //
        // 所以这里先把「新值优先、未传则沿用旧值」合并出来，再拿合并结果去校验。
        QuestionType finalType = dto.getType() != null ? dto.getType() : existing.getType();
        List<QuestionOption> finalOptions =
                dto.getOptions() != null ? dto.getOptions() : existing.getOptions();
        String finalAnswer = dto.getAnswer() != null ? dto.getAnswer() : existing.getAnswer();

        validateByType(finalType, finalOptions, finalAnswer);

        Question update = new Question();
        update.setId(id);
        update.setContent(dto.getContent());
        update.setType(dto.getType());
        update.setOptions(dto.getOptions());
        update.setAnswer(dto.getAnswer());
        update.setScore(dto.getScore());
        update.setDifficulty(dto.getDifficulty());

        questionMapper.updateById(update);

        log.info("修改题目成功: id={}", id);
        return QuestionConverter.toVO(questionMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (questionMapper.selectById(id) == null) {
            throw new BizException(ResultCode.QUESTION_NOT_FOUND);
        }

        // ---------- 引用检查 ----------
        // 题目被试卷引用时不能删。否则那张试卷里会少一道题，
        // 但 paper_question 里还留着记录，学生答题时会拿到一个空题目。
        //
        // 注意这里要排掉"已被删除的试卷"：
        // 如果一张试卷被逻辑删除了，它的 paper_question 关联行还在表里，
        // 但那些引用已经不算数了 —— 不应该因为它们而禁止删除题目。
        List<PaperQuestion> refs = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getQuestionId, id));

        if (!refs.isEmpty()) {
            // 这里用 Set 去重：同一张试卷只会引用一道题一次（有唯一索引保证），
            // 但多张试卷会产出多个 paperId
            Set<Long> paperIds = refs.stream()
                    .map(PaperQuestion::getPaperId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // selectCount 会被 @TableLogic 自动加上 deleted = 0，
            // 所以这里数出来的正是"还活着的"试卷数量
            Long alivePapers = paperMapper.selectCount(
                    new LambdaQueryWrapper<Paper>().in(Paper::getId, paperIds));

            if (alivePapers != null && alivePapers > 0) {
                throw new BizException(ResultCode.QUESTION_IN_USE,
                        "该题目已被 " + alivePapers + " 张试卷引用，请先从这些试卷中移除");
            }
        }

        // 逻辑删除：把 deleted 置 1。历史考试记录里引用这道题的地方仍然查得到内容
        questionMapper.deleteById(id);
        log.info("删除题目: id={}", id);
    }

    @Override
    public QuestionVO getById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(ResultCode.QUESTION_NOT_FOUND);
        }
        return QuestionConverter.toVO(question);
    }

    @Override
    public PageResult<QuestionVO> page(QuestionQueryDTO query) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        // like 的第一个参数是条件：false 时整个条件不拼进 SQL。
        // 这样就不用写一串 if 来判断"这个筛选项传了没有"，
        // 仍然是【类型安全】的 —— 字段名用方法引用，改名时编译期就报错
        wrapper.like(StringUtils.hasText(query.getKeyword()),
                        Question::getContent, query.getKeyword())
                .eq(query.getType() != null, Question::getType, query.getType())
                .eq(query.getDifficulty() != null, Question::getDifficulty, query.getDifficulty())
                .eq(query.getCreatorId() != null, Question::getCreatorId, query.getCreatorId())
                .orderByDesc(Question::getCreateTime);

        Page<Question> page = new Page<>(query.getPage(), query.getSize());
        IPage<Question> result = questionMapper.selectPage(page, wrapper);
        return PageResult.of(result, QuestionConverter::toVO);
    }

    // ==================== 按题型校验 ====================

    /**
     * 校验题目的完整性。规则随题型变化。
     * <p>
     * 用 {@code switch} 表达式（Java 14+）而不是 if-else 链：
     * 编译器会检查枚举分支是否齐全，将来新增题型时，
     * 这里会直接编译不过，逼着你补上对应的规则 ——
     * 而不是等线上出现一道"没人知道该怎么判分"的题。
     */
    private void validateByType(QuestionType type, List<QuestionOption> options, String answer) {
        if (type == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "题型不能为空");
        }
        if (!StringUtils.hasText(answer)) {
            throw new BizException(ResultCode.PARAM_ERROR, "答案不能为空");
        }

        switch (type) {
            case SINGLE -> {
                Set<String> keys = requireOptionKeys(options, 2);
                // 答案必须正好是其中一个选项的 key。
                // 如果教师填了 "E" 而选项只有 A-D，这道题将永远没人能答对 ——
                // 而且不会有任何报错，直到考试结束才发现全班这题都是 0 分
                if (!keys.contains(answer.trim())) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "单选题的答案必须是选项之一，当前选项为 " + keys);
                }
            }
            case MULTIPLE -> {
                Set<String> keys = requireOptionKeys(options, 2);
                Set<String> answerKeys = splitAnswer(answer);

                if (answerKeys.size() < 2) {
                    // 只有一个答案的多选题，本质上就是单选题。
                    // 允许它的后果是判分逻辑要额外处理这种退化情况，
                    // 不如在录入时就说清楚
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "多选题的答案至少需要 2 个选项，只有一个答案请改用单选题");
                }
                // 求差集：答案里有、选项里没有的那些 key
                Set<String> invalid = new HashSet<>(answerKeys);
                invalid.removeAll(keys);
                if (!invalid.isEmpty()) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "答案中包含不存在的选项：" + invalid + "，当前选项为 " + keys);
                }
                if (answerKeys.size() != keys.size() && answerKeys.size() > keys.size()) {
                    // 理论上走不到这里（上面已经保证 answerKeys ⊆ keys），
                    // 保留作为断言性质的检查
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID, "答案选项数量异常");
                }
            }
            case JUDGE -> {
                // 判断题不需要选项，但答案只能是"对"或"错"。
                // 允许"正确"、"T"、"√" 这类写法会让判分变得脆弱 ——
                // 学生提交"对"、标准答案是"正确"，字符串比对失败，判成错
                String normalized = answer.trim();
                if (!"对".equals(normalized) && !"错".equals(normalized)) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "判断题的答案只能是「对」或「错」");
                }
            }
            case ESSAY -> {
                // 简答题不该有选项 —— 有选项的简答题会让学生困惑：
                // 到底是随便写一段，还是从里面选？
                if (options != null && !options.isEmpty()) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "简答题不应包含选项，请清空选项后重试");
                }
                // 答案长度已经在 DTO 上用 @Size 限制了
            }
        }
    }

    /**
     * 校验选项列表，返回所有选项 key 的集合。
     *
     * @param minCount 最少需要几个选项
     */
    private Set<String> requireOptionKeys(List<QuestionOption> options, int minCount) {
        if (options == null || options.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "该题型必须提供选项");
        }
        if (options.size() < minCount) {
            throw new BizException(ResultCode.PARAM_ERROR, "至少需要 " + minCount + " 个选项");
        }

        Set<String> keys = new LinkedHashSet<>();
        for (QuestionOption option : options) {
            if (option == null || !option.hasContent()) {
                throw new BizException(ResultCode.PARAM_ERROR, "选项的标识和内容都不能为空");
            }
            String key = option.key().trim();
            // 重复的 key 是灾难性的：学生选了 A，判分时不知道该按哪个 A 算分。
            // 这里用 Set.add 的返回值判断重复，而不是 add 之后再比 size，
            // 因为前者能立刻定位到是哪个 key 重复了
            if (!keys.add(key)) {
                throw new BizException(ResultCode.PARAM_ERROR, "选项标识重复：" + key);
            }
        }
        return keys;
    }

    /**
     * 把多选题答案拆成集合。
     * <p>
     * 做了两件容错：
     * <ul>
     *   <li>忽略大小写 —— 教师填 "a,b" 和 "A,B" 应该等价</li>
     *   <li>忽略空格 —— "A, B, D" 这种带空格的写法很常见</li>
     * </ul>
     * 返回 {@code LinkedHashSet} 而非 {@code HashSet}：
     * 出错信息里打印出来的顺序会和教师填写的顺序一致，便于他自己核对。
     */
    private Set<String> splitAnswer(String answer) {
        return Arrays.stream(answer.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
