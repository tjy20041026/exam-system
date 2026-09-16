# 在线考试系统（exam-system）

> **作者：唐靖祎** —— 个人后端项目

一个纯后端的在线考试系统 REST API 服务，覆盖「出题 → 组卷 → 考试 → 交卷 → 判分 → 查成绩」完整闭环。

**不做前端**，接口文档（`/doc.html`）本身即是演示界面。

---

## 技术栈

| 层次 | 选型 | 版本 |
|---|---|---|
| 语言 / 运行时 | Java | 21 (LTS) |
| 框架 | Spring Boot | 3.5.16 |
| 持久层 | MyBatis-Plus | 3.5.17 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 6.2 |
| 认证 | JJWT（手写拦截器，未引入 Spring Security） | 0.12.7 |
| 接口文档 | springdoc-openapi + Knife4j UI | 2.8.17 / 4.5.0 |
| 构建 | Maven Wrapper | — |

---

## 快速开始

### 1. 建库

用 Navicat 或命令行依次执行：

```bash
mysql -u root -p < sql/00_init_account.sql   # 建库 + 建应用专用账号 exam_app
mysql -u root -p < sql/01_schema.sql         # 建 6 张表
```

> `00_init_account.sql` 里的密码是占位符 `<你的密码>`，执行前先改成你自己的。
> 已有旧库的环境，若升级过版本，额外执行 `sql/03_alter_sys_user_drop_deleted.sql`。

### 2. 配置数据库密码

数据库密码不在 `application.yml` 里，而是放在 `application-local.yml`，后者被 `.gitignore` 排除，不会进版本库。

```bash
cp application-local.yml.example application-local.yml
# 然后编辑 application-local.yml，把 password 改成第 1 步里设的密码
```

> 密码属于部署环境，不进代码。写进 `application.yml` 再删也没用，git 历史里查得到。

### 3. 启动 Redis

项目使用独立的 6380 端口实例，与机器上其他 Redis 互不干扰：

```bash
tools/redis/start-redis.bat
```

> `tools/` 未纳入版本库（约 21MB 的 Redis 二进制）。需要自备一份 Windows 版 Redis 放在 `tools/redis/`，并把 `redis.conf` 端口改成 6380。

### 4. 启动应用

```bash
./mvnw spring-boot:run        # Windows 下用 mvnw.cmd
```

启动后访问接口文档：**http://127.0.0.1:8080/doc.html**

> 必须用 `127.0.0.1` 而非 `localhost`，且用 `http`。浏览器有「始终使用安全连接」设置时会把 `localhost` 升级成 `https`，而本服务不提供 TLS，会显示「无法访问此网页」。

### 5. 初始账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 管理员 |
| `teacher01` | `Teacher@123` | 教师 |
| `student01` | `Student@123` | 学生 |

> 演示用弱密码，仅限本地。**这三个密码与数据库密码无关**，是业务账号，存在 `sys_user` 表里（BCrypt 哈希，不是明文）。

---

## 接口使用方式

先调 `POST /api/auth/login` 拿 token，再在 Knife4j 页面右上角「Authorize」填入，后续请求会自动带上：

```
Authorization: Bearer <token>
```

---

## 项目结构

```
shixi/
├── sql/                        建库建表脚本、种子数据
├── tools/redis/                项目专用 Redis 实例（6380 端口）
├── jmeter/                     并发交卷压测计划与实测结果
└── src/main/java/com/exam/
    ├── common/                 统一响应体、状态码、异常、登录上下文
    ├── config/                 MyBatis-Plus / Redis / Jackson / MVC / 接口文档 配置
    ├── annotation/             自定义注解（@RequiresRole）
    ├── interceptor/            JWT 认证拦截器
    ├── util/                   JWT 工具类
    ├── entity/ mapper/         数据层
    ├── service/ + impl/        业务层
    ├── controller/             接口层
    ├── dto/ vo/                入参 / 出参
    └── converter/              实体与 VO 的转换
```

---

## 设计要点

几处关键取舍：

- **`sys_user` 不做逻辑删除** —— 逻辑删除与唯一索引存在根本冲突，详见 `sql/01_schema.sql`
- **登录失败提示刻意含糊** —— 账号不存在与密码错误返回完全相同的提示，堵死账号枚举通道
- **JWT 无状态但不引入 Spring Security** —— 用拦截器手写，逻辑完全透明可控
- **`ThreadLocal` 存登录上下文** —— 必须在 `afterCompletion` 中清理，否则线程池复用会串号
- **两层权限** —— URL 层（`/api/**` 全拦）保证"必须登录"，`@RequiresRole` 注解保证"必须是某个角色"。注意注解的默认值是**放行**，所以这是 fail-open 的：漏标注解的方法会放行，靠 URL 层兜底
- **未知路径的 404 单独处理** —— 不处理的话会掉进兜底 handler 被当成"程序缺陷"打满屏 ERROR
- **考试接口单独建 `ExamQuestionVO`** —— 不复用带 `answer` 的 `QuestionVO`，让"泄题"这个错误在类型层面无法表达
- **交卷用条件 UPDATE 抢锁** —— `WHERE id=? AND status='ONGOING'`，靠 InnoDB 行锁保证只有一个请求拿到 `affectedRows==1`
- **判分遍历 `paper_question` 而非学生提交的答案** —— 否则白卷的分子分母会一起归零，得分率反而显示满分
- **Redis 是加速层，不是唯一数据源** —— 所以 `ExamSessionService` 所有方法都不抛异常，Redis 挂了就降级写数据库
- **`getAnswers` 返回 `Optional`** —— `Optional.empty()`（去查数据库）和 `of(空Map)`（确实没答）必须区分，搞混等于清空考生答卷

---

## 考试接口一览

学生登录后（`student01` / `Student@123`）走完整流程：

| 步骤 | 接口 | 说明 |
|---|---|---|
| 1 | `POST /api/exams/start/{paperId}` | 开考。**幂等** —— 重复调用返回同一场考试，不会重新计时 |
| 2 | `PUT /api/exams/{id}/answer` | 答题。幂等，同题反复提交只保留最后一次 |
| 3 | `GET /api/exams/{id}` | 刷新 / 断点续考，带已答内容和服务端算的剩余秒数 |
| 4 | `POST /api/exams/{id}/submit` | 交卷 + 自动判分 |
| 5 | `GET /api/exams/{id}/result` | 成绩单 |
| 6 | `GET /api/exams/my` | 我的考试列表 |

> 返回的题目**不含标准答案**，交卷后也不返回 —— 同一张卷子会被多个学生考且时间窗口重叠，
> 先交卷的人看到答案就能泄题。

---

## 功能

- **用户管理** —— 管理员 / 教师 / 学生三种角色，密码 BCrypt 存储
- **认证** —— JWT + 拦截器，URL 层拦登录、注解层拦角色
- **题库** —— 单选 / 多选 / 判断 / 简答四种题型，按类型和难度筛选
- **组卷** —— 选题设分，试卷状态机（草稿 / 已发布 / 已结束）
- **考试** —— 开考 / 答题 / 交卷 / 自动判分 / 查成绩完整闭环
- **断点续考** —— 作答态存 Redis，换设备或刷新后能恢复现场，剩余时间以服务端为准
- **防重复交卷** —— 条件更新抢锁，配 JMeter 50 / 100 线程并发验证
