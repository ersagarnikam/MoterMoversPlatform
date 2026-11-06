package uk.co.motormovers.platform;

import static org.mockito.Mockito.mock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

/**
 * Keeps the scheduled jobs from firing by the clock during tests, where a reminder at ten past the hour lands in the
 * middle of a test that counts what was sent. The jobs are still registered, on a scheduler that never runs them, and
 * tests call them directly. It sits in the test sources, where the application's component scan finds it for every
 * test context, so the production jar never contains it.
 */
@Configuration(proxyBeanMethods = false)
class NoScheduledJobsInTests {

    @Bean
    TaskScheduler taskScheduler() {
        return mock(TaskScheduler.class);
    }
}
