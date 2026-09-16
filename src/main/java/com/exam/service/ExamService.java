package com.exam.service;

import com.exam.dto.SaveAnswerDTO;
import com.exam.vo.ExamPaperVO;
import com.exam.vo.ExamRecordVO;
import com.exam.vo.ExamResultVO;

import java.util.List;

/**
 * 考试主流程服务。
 *
 * <h3>本接口只暴露 5 个动作，覆盖了 "开考 → 答题 → 交卷 → 查成绩" 的完整闭环</h3>
 * <pre>
 *   startExam   开考（同时也是"恢复考试"的入口）
 *   getPaper    取卷（断点续考时把已答内容一起带回来）
 *   saveAnswer  保存单题答案
 *   submit      交卷 + 判分
 *   getResult   查成绩
 *   myRecords   我的考试列表
 * </pre>
 *
 * <h3>为什么每个方法都带 examRecordId 而不是 paperId</h3>
 * <p>
 * 因为"考哪张卷"只是开考那一刻需要的信息。一旦开考，
 * 后续所有操作的<b>真正主体是"这场考试"</b>，而不是"那张试卷"。
 * <p>
 * 同一个学生、同一张试卷，永远只对应一条 {@code exam_record}（数据库有唯一索引兜底）。
 * 用 examRecordId 作为后续操作的入口，等于把"这场考试"唯一确定了 ——
 * 剩下的"是谁的考试""做到哪一步了""什么时候截止"全都能从这条记录推出来，
 * 调用方不需要再传 userId 之类的东西（那些从登录态取，客户端传的一律不可信）。
 */
public interface ExamService {

    /**
     * 开考。生成考试记录并返回试卷内容。
     *
     * <p>
     * <b>这个方法被设计成幂等的</b>：如果该学生在这张试卷上已经有进行中的考试记录，
     * 直接返回那条记录的试卷内容，而不是报错。
     * <p>
     * 为什么要这样？因为"开考"这个动作在真实使用中会重复触发 ——
     * 学生点了一下没反应又点了一下、网络超时重试、刷新页面重进考场。
     * 如果第一次之后都报"你已经开始考试了"，学生就被卡在门外了：
     * 他知道自己在考试，却拿不到题目。
     * <p>
     * 这背后是接口设计里一条很实用的原则 ——
     * <b>把"重复调用"变成一个正常的结果，而不是一个错误</b>。
     * 客户端因此不需要自己去判断"我到底开考了没有"，重试逻辑也就简单了。
     *
     * @param paperId 试卷 ID
     * @return 试卷内容（含截止时间、剩余秒数）
     */
    ExamPaperVO startExam(Long paperId);

    /**
     * 取卷 —— 答题过程中随时可以调，用于刷新和断点续考。
     *
     * <p>
     * 返回内容包含<b>已作答的答案</b>，所以学生关掉浏览器再打开、
     * 或者换一台电脑重新登录，调用这个方法就能完整恢复现场。
     * <p>
     * Day 5 的答案从 MySQL 读；Day 6 会改成优先读 Redis。
     * 接口签名不变 —— <b>换存储不动接口，这是先设计接口再选实现的收益。</b>
     */
    ExamPaperVO getPaper(Long examRecordId);

    /**
     * 保存单题答案。
     * <p>
     * 每答一题调一次。用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 写入，
     * 所以同一个题目反复修改答案不会产生多条记录。
     */
    void saveAnswer(Long examRecordId, SaveAnswerDTO dto);

    /**
     * 交卷。抢锁 → 判分 → 落库。
     *
     * <p>
     * <b>这是整个项目里并发最危险的一个接口</b>，也是 Day 6 的重点，
     * 因为：
     * <ul>
     *   <li>学生可能手抖点两次交卷按钮</li>
     *   <li>前端可能在超时后自动重试</li>
     *   <li>前端倒计时归零时自动交卷 —— 而学生此刻可能正在手动点交卷</li>
     * </ul>
     * 这些情况会真的同时发生，不是理论假设。
     * <p>
     * 防重复的核心是 {@code UPDATE ... WHERE id = ? AND status = 'ONGOING'}：
     * 判断和修改被压进同一条 SQL，靠 InnoDB 行锁保证只有一个请求能拿到
     * {@code affectedRows == 1}。详见 {@code ExamRecordMapper#finishExam}。
     */
    void submit(Long examRecordId);

    /**
     * 查成绩单。
     * <p>
     * 未交卷时返回 {@code EXAM_NOT_SUBMITTED} 错误而不是一条空成绩 ——
     * 因为"还没考完"和"考了 0 分"是完全不同的两件事，
     * 返回一个 score=null 的空对象会让前端很难区分这两种情况。
     */
    ExamResultVO getResult(Long examRecordId);

    /** 我的考试列表，按开考时间倒序 */
    List<ExamRecordVO> myRecords();

    // ==================================================================
    //  以下方法【仅供定时任务调用】，不对应任何 HTTP 接口
    // ==================================================================

    /**
     * 自动交卷（超时兜底）。<b>不做归属校验</b>。
     *
     * <h3>⚠️ 为什么这个方法故意不做 assertOwner</h3>
     * <p>
     * 因为它没有"当前登录用户"这个概念 —— 调用它的是定时任务，
     * 不是某个人的请求。定时任务的职责恰恰是<b>替所有超时的人交卷</b>，
     * 所以它天然要跨越所有用户。
     * <p>
     * 那为什么这样是安全的？关键在于<b>谁能调到这里</b>：
     * <ul>
     *   <li>本方法<b>没有对应的 Controller 接口</b>，
     *       外部请求无论怎么构造 URL 都打不到它</li>
     *   <li>唯一的调用点是 {@link #findOverdueExamRecordIds}，
     *       而那个查询的条件是 {@code status='ONGOING' AND deadline < now} ——
     *       <b>只有真的超时了的记录才会被交卷</b>，
     *       不是"随便传个 ID 就能替别人交卷"</li>
     * </ul>
     * <p>
     * <b>越权防护的落点不一定在方法内部。</b>
     * 这里的防护是"这个方法根本不可达 + 调用方的筛选条件足够严格"。
     * 判断一个方法安不安全，要看它的可达路径，不能只看它有没有写校验。
     * <p>
     * 反过来说：如果哪天要给这个方法加一个 Controller 接口（比如教师手动
     * 强制某个学生交卷），那就<b>必须</b>补上角色和归属校验 ——
     * 因为可达性变了，"安全"的前提也就不成立了。
     */
    void autoSubmit(Long examRecordId);

    /**
     * 查出所有超时但还没交卷的考试记录 ID。给定时任务用。
     *
     * @param limit 单次处理上限。必须有，理由见
     *              {@code ExamRecordMapper#findOverdue}
     */
    List<Long> findOverdueExamRecordIds(int limit);

    /**
     * 兜底落库：把进行中考试的 Redis 作答态刷进数据库。
     *
     * <h3>这个方法存在的意义：给"Redis 会挂"这件事一个交代</h3>
     * <p>
     * 作答态放在 Redis 里，读写快，但 Redis 是内存数据库 ——
     * 机器断电、磁盘故障、或者被 {@code FLUSHDB} 清掉，数据就没了。
     * （本项目实例开了 AOF 持久化，所以单纯的<b>进程重启</b>不会丢数据 ——
     * 但 AOF 的 everysec 策略意味着最坏仍可能丢最后 1 秒的写入，
     * 机器级故障则可能丢得更多。这里不能假设"Redis 一定记得住"。）
     * 如果答案只存在 Redis 里，上面任何一件事发生，
     * <b>学生答了 40 分钟的卷子瞬间归零</b>。这是不可接受的。
     * <p>
     * 所以需要一个定时任务，每隔一段时间把 Redis 里的作答内容
     * 同步一份到 MySQL。这样最坏情况下（Redis 完全丢失），
     * 学生也只损失"最后一次落库之后"的那几道题。
     * <p>
     * 这叫做<b>最终一致性</b>：Redis 和 MySQL 之间的数据不是时刻相同的，
     * 但会在一段有界的时间后收敛到一致。
     * <p>
     * <b>为什么不做成"每次答题都写两份"（写穿透）</b>：
     * 那就等于每次答题都要访问一次数据库，Redis 的存在意义（
     * 把高频写从数据库卸下来）就没了。
     * <b>"多久同步一次"就是在"数据安全"和"数据库压力"之间选一个平衡点。</b>
     *
     * @param limit 单次处理的考试场数上限
     * @return 实际成功落库的考试场数
     */
    int flushOngoingSessions(int limit);
}
