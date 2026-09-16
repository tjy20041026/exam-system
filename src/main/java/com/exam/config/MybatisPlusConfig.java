package com.exam.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 配置。 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁，配合实体上的 @Version 字段，靠 affectedRows 是否为 1 判断有没有被抢先改过
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 防全表更新/删除：代码漏写 where 条件时直接抛异常，而不是清空全表
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 分页拦截器官方要求放最后一个，它会改写 SQL，排前面的话后续拦截器看到的就不是原始 SQL
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限 500，防止有人传 size=999999 把库拖垮
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
