package com.logai.assint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    /**
     * 定义一个专门用于“记录意图”后台处理的线程池
     * Bean 名称被指定为 "recordIntentTaskExecutor"
     */
    @Bean(name = "recordIntentTaskExecutor")
    public TaskExecutor recordIntentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：即使空闲，也会保留的线程数
        executor.setCorePoolSize(5);

        // 最大线程数：队列满后，允许创建的最大线程数
        executor.setMaxPoolSize(10);

        // 队列容量：新任务在创建新线程前排队的容量
        executor.setQueueCapacity(25);

        // 线程空闲时间：超过 CorePoolSize 的线程在空闲多久后被销毁
        executor.setKeepAliveSeconds(60);

        // 线程名前缀：方便日志和监控中识别线程来源
        executor.setThreadNamePrefix("Record-Task-");

        // 拒绝策略：CallerRunsPolicy 表示任务被拒绝时，由调用者线程（主线程）执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化线程池
        executor.initialize();
        return executor;
    }
}