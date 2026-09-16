package com.exam.controller;

import com.exam.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 环境自检接口。
 * <p>
 * Day 1 的产出物：把「Java / Spring Boot / MySQL / Redis / 接口文档」
 * 五个环节逐个打通并可视化，任何一个环节不通都能立刻定位到是哪一环，
 * 而不必等到写业务代码时才发现。
 */
@Slf4j
@Tag(name = "00-环境自检", description = "验证各组件连通性，仅开发期使用")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "服务存活探测", description = "不依赖任何外部组件，只要能返回就说明 Spring Boot 本身是活的")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("application", "exam-system");
        data.put("time", LocalDateTime.now().toString());
        data.put("javaVersion", System.getProperty("java.version"));
        return Result.success(data);
    }

    @Operation(summary = "MySQL 连通性检查", description = "执行 SELECT VERSION()，能拿到版本号即说明数据库已连通")
    @GetMapping("/db")
    public Result<Map<String, Object>> db() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'exam_system'",
                    Integer.class);
            data.put("connected", true);
            data.put("mysqlVersion", version);
            data.put("tablesInExamSystem", tableCount);
            data.put("hint", tableCount != null && tableCount >= 6
                    ? "表已建好（应有 6 张）"
                    : "表数量不足 6 张，请先执行 sql/01_schema.sql");
        } catch (Exception e) {
            log.error("MySQL 连通性检查失败", e);
            data.put("connected", false);
            data.put("error", e.getMessage());
            data.put("hint", "检查 application-local.yml 里的密码是否正确（模板见 application-local.yml.example），以及是否已执行 sql/01_schema.sql");
        }
        return Result.success(data);
    }

    @Operation(summary = "Redis 连通性检查", description = "执行 SET + GET 往返，验证读写均正常")
    @GetMapping("/redis")
    public Result<Map<String, Object>> redis() {
        Map<String, Object> data = new LinkedHashMap<>();
        String key = "exam:health:ping";
        try {
            String value = "pong-" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(key, value);
            String readBack = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);

            data.put("connected", true);
            data.put("writeReadMatch", value.equals(readBack));
            data.put("hint", "期望连到 6380 端口的项目专用实例。若连到了 6379 说明配置错了");
        } catch (Exception e) {
            log.error("Redis 连通性检查失败", e);
            data.put("connected", false);
            data.put("error", e.getMessage());
            data.put("hint", "Redis 没启动？双击 tools/redis/start-redis.bat");
        }
        return Result.success(data);
    }

    @Operation(summary = "全组件一次性自检", description = "一次性检查所有依赖，Day 1 验收用这个")
    @GetMapping("/all")
    public Result<Map<String, Object>> all() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("springBoot", "运行中");
        data.put("java", System.getProperty("java.version"));

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("mysql", "正常");
        } catch (Exception e) {
            data.put("mysql", "异常: " + e.getMessage());
        }

        try {
            redisTemplate.opsForValue().set("exam:health:all", "1");
            redisTemplate.delete("exam:health:all");
            data.put("redis", "正常");
        } catch (Exception e) {
            data.put("redis", "异常: " + e.getMessage());
        }

        data.put("apiDocs", "http://localhost:8080/doc.html");
        return Result.success(data);
    }
}
