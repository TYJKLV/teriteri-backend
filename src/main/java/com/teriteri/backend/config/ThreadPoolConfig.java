package com.teriteri.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static com.teriteri.backend.utils.ConstantsUtil.BLOCKING_QUEUE;

@Configuration
@EnableAsync    // 开启异步，配合 @Async；实际上，项目中并没有用到此注解
public class ThreadPoolConfig {

    @Bean("taskExecutor")
    public Executor asyncServiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //设置核心线程数 (跟 CPU核心数保持一致)
        executor.setCorePoolSize(16);
        //设置最大线程数
        executor.setMaxPoolSize(32);
        //配置队列大小 100
        executor.setQueueCapacity(BLOCKING_QUEUE);
        //设置线程活跃时间（秒）
        executor.setKeepAliveSeconds(60);
        //设置默认线程名称前缀
        executor.setThreadNamePrefix("teriteri");
        //等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //执行初始化
        executor.initialize();
        return executor;
    }

}
