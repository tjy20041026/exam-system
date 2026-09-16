package com.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 在线考试系统启动类。作者：唐靖祎 */
@SpringBootApplication
// 开启定时任务。不开的话 @Scheduled 不报错也不生效，排查时容易往业务代码上找
@EnableScheduling
public class ExamSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExamSystemApplication.class, args);
	}

}
