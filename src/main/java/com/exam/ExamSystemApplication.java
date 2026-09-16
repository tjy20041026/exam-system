package com.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 在线考试系统启动类。
 *
 * <p>
 * 作者：唐靖祎
 */
@SpringBootApplication
// 开启定时任务支持。不开的话 @Scheduled 注解【不会报错】，只是静默不生效 ——
// 这是新手很容易踩的坑：代码看着完全正常，日志里没有任何异常，
// 但任务就是不执行。排查半天最后发现是少了一个注解。
@EnableScheduling
public class ExamSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExamSystemApplication.class, args);
	}

}
