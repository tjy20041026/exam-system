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
 * <p>
 * 题目"完整"的定义随题型变化，这些规则统一收在 {@link #validateByType} 里，
 * 不散落到 create / update 各处。
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
        // 出题人从登录态取，不接受客户端传入 —— 让前端传 creatorId 的话，
        // 任何人都能把题目挂到别人名下
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

        // 校验必须基于"合并后的最终状态"：只把 type 改成 MULTIPLE、answer 不动时，
        // 若只看 dto 里的字段就会发现 answer 为 null 而跳过校验，
        // 库里就多出一道"多选题只有一个答案"的脏数据
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

        // 题目被试卷引用时不能删，否则那张试卷会少一道题：
        // paper_question 里还留着记录，学生答题时拿到一个空题目。
        // 已被逻辑删除的试卷不算 —— 它的关联行还在表里，但引用已经失效
        List<PaperQuestion> refs = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getQuestionId, id));

        if (!refs.isEmpty()) {
            Set<Long> paperIds = refs.stream()
                    .map(PaperQuestion::getPaperId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // selectCount 会被 @TableLogic 自动加上 deleted = 0，数出来的是还活着的试卷
            Long alivePapers = paperMapper.selectCount(
                    new LambdaQueryWrapper<Paper>().in(Paper::getId, paperIds));

            if (alivePapers != null && alivePapers > 0) {
                throw new BizException(ResultCode.QUESTION_IN_USE,
                        "该题目已被 " + alivePapers + " 张试卷引用，请先从这些试卷中移除");
            }
        }

        // 逻辑删除。历史考试记录里引用这道题的地方仍然查得到内容
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
        // like / eq 的第一个参数是条件，false 时整条不拼进 SQL，
        // 省掉一串判断"筛选项传了没有"的 if
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

    /** 按题型校验完整性。用 switch 表达式，将来新增题型时编译期就报错，逼着补上规则 */
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
                // 答案必须是选项之一。教师填了 "E" 而选项只有 A-D 的话，
                // 这道题永远没人能答对，而且一路不报错，考完才发现全班这题都是 0 分
                if (!keys.contains(answer.trim())) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "单选题的答案必须是选项之一，当前选项为 " + keys);
                }
            }
            case MULTIPLE -> {
                Set<String> keys = requireOptionKeys(options, 2);
                Set<String> answerKeys = splitAnswer(answer);

                if (answerKeys.size() < 2) {
                    // 只有一个答案的多选题本质就是单选，允许它判分逻辑就得
                    // 额外处理这种退化情况，不如在录入时就说清楚
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "多选题的答案至少需要 2 个选项，只有一个答案请改用单选题");
                }
                Set<String> invalid = new HashSet<>(answerKeys);
                invalid.removeAll(keys);
                if (!invalid.isEmpty()) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "答案中包含不存在的选项：" + invalid + "，当前选项为 " + keys);
                }
                if (answerKeys.size() != keys.size() && answerKeys.size() > keys.size()) {
                    // 理论上走不到（上面已保证 answerKeys ⊆ keys），留作断言
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID, "答案选项数量异常");
                }
            }
            case JUDGE -> {
                // 只收"对/错"两种写法。允许"正确"、"T"、"√" 的话，
                // 学生答"对"、标准答案写"正确"，字符串比对失败就被判错
                String normalized = answer.trim();
                if (!"对".equals(normalized) && !"错".equals(normalized)) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "判断题的答案只能是「对」或「错」");
                }
            }
            case ESSAY -> {
                // 简答题带选项会让学生困惑：到底是随便写一段，还是从里面选？
                if (options != null && !options.isEmpty()) {
                    throw new BizException(ResultCode.QUESTION_ANSWER_INVALID,
                            "简答题不应包含选项，请清空选项后重试");
                }
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
            // 重复的 key 会让学生选了 A 之后判分不知道该按哪个 A 算分。
            // 用 Set.add 的返回值判断，比 add 完再比 size 能直接定位到是哪个 key 重复
            if (!keys.add(key)) {
                throw new BizException(ResultCode.PARAM_ERROR, "选项标识重复：" + key);
            }
        }
        return keys;
    }

    /**
     * 把多选题答案拆成集合：忽略大小写和空格（"a,b"、"A, B" 都应该等价）。
     * 用 LinkedHashSet 是让报错信息里的顺序和教师填写的一致。
     */
    private Set<String> splitAnswer(String answer) {
        return Arrays.stream(answer.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
