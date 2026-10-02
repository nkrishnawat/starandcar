package com.resilientechnology.starandcar.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Threading for STARMail.
 *
 * <p>The carbon-copy e-mail leg gets its own small pool so it can run in parallel with, and
 * completely independently of, the browser-delivery leg. Keeping it off the default
 * {@code applicationTaskExecutor} means a slow or unreachable SMTP server can never starve
 * the delivery of STARMail messages to browsers.</p>
 */
@Configuration
public class StarMailConfig {

    @Bean(name = "starMailEmailExecutor")
    public Executor starMailEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("starmail-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
