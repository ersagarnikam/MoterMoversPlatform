package uk.co.motormovers.platform.seed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseLesson;
import uk.co.motormovers.platform.training.model.CourseLessonProgress;
import uk.co.motormovers.platform.training.model.CourseQuiz;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.model.QuizAttempt;
import uk.co.motormovers.platform.training.repository.CourseLessonProgressRepository;
import uk.co.motormovers.platform.training.repository.CourseLessonRepository;
import uk.co.motormovers.platform.training.repository.CourseQuizRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.QuizAttemptRepository;
import uk.co.motormovers.platform.user.model.AppUser;

/**
 * Demo places on the recorded courses (go-live task 89): a practitioner and a parent part-way through, and a head who
 * has finished, with the certificate. Nothing is emailed.
 */
@Component
class DemoCourses {

    private final EnrolmentRepository enrolments;
    private final CourseLessonRepository lessons;
    private final CourseLessonProgressRepository progress;
    private final CourseQuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final Clock clock;

    DemoCourses(EnrolmentRepository enrolments, CourseLessonRepository lessons,
            CourseLessonProgressRepository progress, CourseQuizRepository quizzes, QuizAttemptRepository attempts,
            Clock clock) {
        this.enrolments = enrolments;
        this.lessons = lessons;
        this.progress = progress;
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * @param partWay    a practitioner who has done the first lesson of the professional course
     * @param certified  someone who has done every lesson and passed its test, so holds the certificate
     * @param parentUser a parent who has done the first lesson of the parent course
     */
    void seed(Course professional, Course parent, AppUser partWay, AppUser certified, AppUser parentUser) {
        Instant now = clock.instant();
        doFirst(enrolments.save(new Enrolment(professional, partWay)), 1, now.minus(2, ChronoUnit.DAYS));
        doFirst(enrolments.save(new Enrolment(parent, parentUser)), 1, now.minus(1, ChronoUnit.DAYS));

        Enrolment done = enrolments.save(new Enrolment(professional, certified));
        doFirst(done, Integer.MAX_VALUE, now.minus(20, ChronoUnit.DAYS));
        for (CourseQuiz quiz : quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(professional.getId())) {
            attempts.save(new QuizAttempt(quiz.getId(), done.getId(), 90, true, now.minus(20, ChronoUnit.DAYS)));
        }
        done.issueCertificateIfEligible(LocalDate.now(clock).minusDays(20), true, true);
        enrolments.save(done);
    }

    private void doFirst(Enrolment e, int howMany, Instant at) {
        List<CourseLesson> approved = lessons.findByCourseIdAndStatusOrderByPositionAscIdAsc(e.getCourse().getId(),
                ContentStatus.APPROVED);
        approved.stream().limit(howMany)
                .forEach(l -> progress.save(new CourseLessonProgress(e.getId(), l.getId(), at)));
    }
}
