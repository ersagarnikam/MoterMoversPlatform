package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** The jobs production runs are still declared on their schedules; in tests they go to a scheduler that never fires. */
@SpringBootTest
class ScheduledJobsTest {

    @Autowired
    TaskScheduler scheduler;

    @Autowired
    ScheduledTaskHolder jobs;

    @Test
    void theJobsAreDeclaredButNeverFireInTests() {
        assertThat(jobs.getScheduledTasks()).extracting(t -> t.getTask())
                .filteredOn(CronTask.class::isInstance).map(CronTask.class::cast).extracting(CronTask::getExpression)
                .contains("0 */10 * * * *", "30 */2 * * * *", "0 30 2 * * *", "0 0 8 * * *");
        assertThat(mockingDetails(scheduler).isMock()).isTrue();
    }
}
