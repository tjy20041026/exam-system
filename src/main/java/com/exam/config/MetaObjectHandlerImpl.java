package com.exam.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充。
 * <p>
 * 实体上的 {@code createTime} / {@code updateTime} 只要标了
 * {@code @TableField(fill = FieldFill.INSERT)}，就不必每次手动 set，
 * 由这里统一填。少写重复代码，也避免漏填导致时间为 null。
 * <p>
 * <b>为什么用 Java 时间而不是交给数据库的 DEFAULT CURRENT_TIMESTAMP？</b>
 * 两者其实都行——建表语句里也写了默认值，属于双保险。
 * 用 Java 侧填充的好处是：插入后能立刻拿到准确的时间值用于返回给前端，
 * 不必再查一次库。代价是依赖应用服务器的时间，多实例部署时要求各机器时钟同步。
 */
@Slf4j
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";

    /** 插入时填充：创建时间和更新时间都要填 */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // strictInsertFill 只在字段为 null 时才填充，不会覆盖你手动设的值
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
    }

    /** 更新时只填 updateTime，createTime 不能被改 */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
