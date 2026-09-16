-- 在线考试系统建库建表。会先 DROP 同名表，重复执行等于清空重建。
-- 执行：mysql -u root -p < 01_schema.sql

CREATE DATABASE IF NOT EXISTS `exam_system`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `exam_system`;

-- 按外键依赖倒序清理，保证脚本可重复执行
DROP TABLE IF EXISTS `answer_record`;
DROP TABLE IF EXISTS `exam_record`;
DROP TABLE IF EXISTS `paper_question`;
DROP TABLE IF EXISTS `paper`;
DROP TABLE IF EXISTS `question`;
DROP TABLE IF EXISTS `sys_user`;


-- 表名用 sys_user 而不是 user：MySQL 8 里 USER 是关键字，加反引号能建，
-- 但实体映射、手写 SQL、客户端工具处处要处理转义，加前缀最省事。
--
-- 本表用物理删除，不做逻辑删除。username 上有唯一索引，逻辑删除的行仍然占着
-- 这个唯一键：删掉 teacher01 再重新注册，应用层查询被框架自动加上 deleted = 0
-- 看不见旧行，判定账号可用，INSERT 时却撞 Duplicate entry。
-- 根源是逻辑删除的作用域在应用层，唯一索引的作用域是整张表，两者对不上。
-- 禁用账号用 status = 0 就够了，物理删除留给管理员真正删人的低频操作。
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    `password`    VARCHAR(100) NOT NULL                COMMENT '密码，存 BCrypt 哈希值而非明文',
    `real_name`   VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT / TEACHER / ADMIN',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 禁用。禁用代替了逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户表';


-- options 存 JSON 数组：[{"key":"A","value":"选项一"},{"key":"B","value":"选项二"}]
-- 用 JSON 类型而非 TEXT，是为了 MySQL 8 能校验格式，后续按选项内容检索也能直接用 JSON 函数。
--
-- answer 的内容随 type 变化：
--   SINGLE / JUDGE -> "A" / "对"
--   MULTIPLE       -> "A,B,C"，判分时拆成集合比较，忽略顺序
--   ESSAY          -> 参考答案，留空表示需人工阅卷
CREATE TABLE `question` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `content`     VARCHAR(1000) NOT NULL               COMMENT '题干',
    `type`        VARCHAR(20)  NOT NULL                COMMENT '题型：SINGLE 单选 / MULTIPLE 多选 / JUDGE 判断 / ESSAY 简答',
    `options`     JSON         NULL                    COMMENT '选项，简答题为 NULL',
    `answer`      VARCHAR(500) NOT NULL                COMMENT '标准答案',
    `score`       INT          NOT NULL DEFAULT 5      COMMENT '建议分值，组卷时可覆盖',
    `difficulty`  TINYINT      NOT NULL DEFAULT 2      COMMENT '难度：1 易，2 中，3 难',
    `creator_id`  BIGINT       NOT NULL                COMMENT '出题教师 ID',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_type_difficulty` (`type`, `difficulty`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '题库表';


-- 截止时间不存在这张表：同一张卷子每人开考时间不同，deadline 在开考时
-- 由开考时间 + duration 算出来，写进 exam_record。
CREATE TABLE `paper` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(200) NOT NULL                COMMENT '试卷标题',
    `total_score` INT          NOT NULL DEFAULT 100    COMMENT '总分',
    `duration`    INT          NOT NULL DEFAULT 60     COMMENT '考试时长（分钟）',
    `start_time`  DATETIME     NULL                    COMMENT '开考时间，NULL 表示不限制',
    `end_time`    DATETIME     NULL                    COMMENT '截止时间，NULL 表示不限制',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / FINISHED 已结束',
    `creator_id`  BIGINT       NOT NULL                COMMENT '创建教师 ID',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_creator` (`creator_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '试卷表';


-- score 是有意冗余的，没让程序实时读 question.score：
-- 同一道题在不同试卷里分值可以不同（期末卷 10 分、随堂测 2 分），实时读就做不到。
CREATE TABLE `paper_question` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `paper_id`    BIGINT NOT NULL                COMMENT '试卷 ID',
    `question_id` BIGINT NOT NULL                COMMENT '题目 ID',
    `score`       INT    NOT NULL                COMMENT '本题在本试卷中的分值',
    `sort_order`  INT    NOT NULL DEFAULT 0      COMMENT '题目顺序',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
    KEY `idx_paper_sort` (`paper_id`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '试卷题目关联表';


-- uk_paper_user：同一学生对同一张卷只能有一条记录，在数据库层面兜底防重复开考。
-- 应用层的先查再插在并发下挡不住，撞上 DuplicateKeyException 就说明有人抢跑了。
--
-- idx_status_deadline：给定时任务扫「status=ONGOING 且 deadline 已过」的记录用，省得全表。
-- deadline 在开考时写入，是断点续考的时间基准，剩余时间按它算，不信客户端。
CREATE TABLE `exam_record` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键，也是 Redis session key 的一部分',
    `paper_id`      BIGINT      NOT NULL                COMMENT '试卷 ID',
    `user_id`       BIGINT      NOT NULL                COMMENT '考生 ID',
    `start_time`    DATETIME    NOT NULL                COMMENT '实际开考时间',
    `deadline`      DATETIME    NOT NULL                COMMENT '交卷截止时间 = 开考时间 + 试卷时长',
    `submit_time`   DATETIME    NULL                    COMMENT '实际交卷时间，未交卷为 NULL',
    `score`         INT         NULL                    COMMENT '得分，未判卷为 NULL',
    `duration_used` INT         NULL                    COMMENT '实际用时（秒）',
    `status`        VARCHAR(20) NOT NULL DEFAULT 'ONGOING'
        COMMENT '状态：ONGOING 进行中 / SUBMITTED 已交卷 / GRADED 已判卷 / TIMEOUT 超时自动交卷',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_user` (`paper_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_deadline` (`status`, `deadline`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '考试记录表';


-- uk_record_question：同一场考试同一题只有一条记录，落库配合
-- INSERT ... ON DUPLICATE KEY UPDATE 做幂等，重复交卷和兜底落库都不会产生脏数据。
CREATE TABLE `answer_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `exam_record_id` BIGINT       NOT NULL                COMMENT '考试记录 ID',
    `question_id`    BIGINT       NOT NULL                COMMENT '题目 ID',
    `user_answer`    VARCHAR(500) NULL                    COMMENT '考生答案，NULL 表示未作答',
    `is_correct`     TINYINT      NULL                    COMMENT '是否正确：1 对，0 错，NULL 未判（简答题）',
    `score`          INT          NOT NULL DEFAULT 0      COMMENT '本题实得分',
    `submit_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本题最后作答时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_question` (`exam_record_id`, `question_id`),
    KEY `idx_record` (`exam_record_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '作答明细表';
