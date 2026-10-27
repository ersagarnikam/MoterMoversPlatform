package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.repository.GuidanceArticleRepository;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.screening.repository.ScreeningItemRepository;
import uk.co.motormovers.platform.seed.StarterContentLoader;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.training.repository.CourseQuizRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.QuizQuestionRepository;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/** A real deployment can start from the starter content, loaded as drafts and only onto an empty platform. */
@SpringBootTest(properties = {
    "platform.seed-demo-data=false",
    "platform.seed-starter-content=true",
    "spring.datasource.url=jdbc:h2:mem:starter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
})
class StarterContentTest {

    @Autowired
    StarterContentLoader loader;

    @Autowired
    ScreeningItemRepository items;

    @Autowired
    LibraryResourceRepository resources;

    @Autowired
    InductionLessonRepository induction;

    @Autowired
    HomeActivityRepository homeActivities;

    @Autowired
    GuidanceArticleRepository guidance;

    @Autowired
    CourseRepository courses;

    @Autowired
    ProgrammeSessionRepository sessions;

    @Autowired
    CourseQuizRepository quizzes;

    @Autowired
    QuizQuestionRepository quizQuestions;

    @Autowired
    WordingGuard wordingGuard;

    @Autowired
    AppUserRepository users;

    @Test
    void anEmptyPlatformGetsTheStarterContentAsDrafts() {
        assertThat(items.count()).isEqualTo(12);
        assertThat(induction.findAll()).hasSize(6).allMatch(l -> l.getStatus() == ContentStatus.DRAFT);
        assertThat(resources.findAll()).hasSize(9).allMatch(r -> r.getStatus() == ContentStatus.DRAFT);
        assertThat(homeActivities.findAll()).hasSize(35).allMatch(a -> a.getStatus() == ContentStatus.DRAFT);
        assertThat(guidance.findAll()).hasSize(6).allMatch(g -> g.getStatus() == ContentStatus.DRAFT);
        assertThat(courses.count()).isEqualTo(2);
        // Quizzes between the practitioner course's sessions, in wording the team could save themselves.
        assertThat(quizzes.count()).isEqualTo(3);
        assertThat(wordingGuard.check(quizzes.findAll().stream().map(q -> q.getTitle()).toList())).isEmpty();
        assertThat(wordingGuard.check(quizQuestions.findAll().stream()
                .flatMap(q -> java.util.stream.Stream.concat(java.util.stream.Stream.of(q.getText()), q.options().stream()))
                .toList())).isEmpty();
        // The team writes the daily sessions, and no demo people or schools come with it.
        assertThat(sessions.count()).isZero();
        assertThat(users.count()).isZero();
    }

    @Test
    void itNeverAddsToContentAlreadyThere() {
        long before = resources.count() + induction.count() + courses.count() + items.count();
        loader.run(null);
        assertThat(resources.count() + induction.count() + courses.count() + items.count()).isEqualTo(before);
    }
}
