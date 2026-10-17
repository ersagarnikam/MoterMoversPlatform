package uk.co.motormovers.platform.child.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.config.PlatformProperties;

/** Deletes children's records a set time after they leave. Data we no longer hold cannot be lost. */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final ChildRepository children;
    private final PlatformProperties properties;
    private final Clock clock;

    public RetentionJob(ChildRepository children, PlatformProperties properties, Clock clock) {
        this.children = children;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public int purgeLeavers() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(properties.retentionDays());
        List<Child> expired = children.findByLeftOnBefore(cutoff);
        children.deleteAll(expired);
        if (!expired.isEmpty()) {
            log.info("Retention: deleted {} child records (left before {})", expired.size(), cutoff);
        }
        return expired.size();
    }
}
