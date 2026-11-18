package de.savas.businessinsightapi.infrastructure.config;

import de.savas.businessinsightapi.application.insight.InsightExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class AsyncExecutionConfig {

    /** Bounded pool for asynchronous query runs; a full queue rejects new work instead of piling it up. */
    @Bean(name = "queryRunExecutor")
    public ThreadPoolTaskExecutor queryRunExecutor(InsightExecutionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("query-run-");
        executor.setCorePoolSize(properties.asyncThreads());
        executor.setMaxPoolSize(properties.asyncThreads());
        executor.setQueueCapacity(properties.asyncQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(10, properties.queryTimeoutSeconds() + 5));
        executor.initialize();
        return executor;
    }
}
