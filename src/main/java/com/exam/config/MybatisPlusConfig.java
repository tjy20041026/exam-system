package com.exam.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * <b>注意拦截器的注册顺序</b>：MyBatis-Plus 官方文档明确要求
 * 「分页拦截器必须最后添加」。原因是分页拦截器会改写 SQL，
 * 如果它先执行，后续拦截器看到的就不是原始 SQL 了。
 * 顺序写错的表现是分页查询偶尔返回错误结果，很难排查。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁：配合实体上的 @Version 字段使用
        //    原理是 UPDATE ... WHERE id=? AND version=?，
        //    靠 affectedRows 是否为 1 判断有没有被别人抢先改过
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 2. 防全表更新/删除：拦截不带 WHERE 条件的 UPDATE / DELETE
        //    这是一道安全网。考试系统里有 exam_record 这种关键表，
        //    万一代码写漏了 where 条件，这个拦截器会直接抛异常而不是清空全表
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 3. 分页拦截器 —— 必须放在最后一个，这是官方要求
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数限制：防止有人传 size=999999 把数据库拖垮
        // 超过 500 条就按 500 处理，这是一条很实用的防护
        pagination.setMaxLimit(500L);
        // 溢出总页数后不回到首页（默认 false 会回到首页，容易让人困惑）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
