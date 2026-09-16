package com.exam.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充。实体上的 createTime / updateTime 标了
 * {@code @TableField(fill = FieldFill.INSERT)} 之后就不必每次手动 set 了。
 * 建表语句里也有 DEFAULT CURRENT_TIMESTAMP，Java 侧再填一遍是为了插入后能立刻拿到时间返回前端。
 */
@Slf4j
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // strictInsertFill 只在字段为 null 时才填，不会覆盖手动设的值
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
