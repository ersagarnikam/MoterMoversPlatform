package uk.co.motormovers.platform.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.screening.repository.ScreeningItemRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;

/**
 * Gives a new deployment the starter content (SEED_STARTER_CONTENT=true), as drafts for the team to check
 * and approve. It only runs on an empty platform, so it never adds to or changes content already entered.
 * The demo loads the same content itself, so this stays off when demo data is on.
 */
@Component
@ConditionalOnExpression("${platform.seed-starter-content:false} and !${platform.seed-demo-data:false}")
public class StarterContentLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StarterContentLoader.class);

    private final StarterContent starterContent;
    private final ScreeningItemRepository items;
    private final LibraryResourceRepository resources;
    private final InductionLessonRepository inductionLessons;
    private final CourseRepository courses;

    public StarterContentLoader(StarterContent starterContent, ScreeningItemRepository items,
            LibraryResourceRepository resources, InductionLessonRepository inductionLessons,
            CourseRepository courses) {
        this.starterContent = starterContent;
        this.items = items;
        this.resources = resources;
        this.inductionLessons = inductionLessons;
        this.courses = courses;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (items.count() > 0 || resources.count() > 0 || inductionLessons.count() > 0 || courses.count() > 0) {
            log.info("Starter content skipped: the platform already has content");
            return;
        }
        starterContent.load(null);
        log.info("Starter content loaded as drafts. Approve it in Content approval before schools start.");
    }
}
