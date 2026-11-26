package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.training.dto.CourseLessonViews;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseLesson;
import uk.co.motormovers.platform.training.model.CourseLessonProgress;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.repository.CourseLessonProgressRepository;
import uk.co.motormovers.platform.training.repository.CourseLessonRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;

/**
 * The lessons of a recorded course (go-live task 89). The team writes each lesson on the course's page and approves
 * it, with the wording check, like an induction lesson. A learner with a place on the course does the approved
 * lessons in order: a lesson opens once the one before it is done, and its film and slides are signed links sent only
 * to that learner (or the team) and only for a lesson that is open. When the last lesson is done the course's quizzes
 * open; a course without quizzes gives its certificate then.
 */
@Service
public class CourseLessonService {

    private final CourseLessonRepository lessons;
    private final CourseLessonProgressRepository progress;
    private final CourseRepository courses;
    private final EnrolmentRepository enrolments;
    private final CertificateService certificates;
    private final AccessPolicy access;
    private final WordingGuard wordingGuard;
    private final FileLinks files;
    private final Clock clock;

    public CourseLessonService(CourseLessonRepository lessons, CourseLessonProgressRepository progress,
            CourseRepository courses, EnrolmentRepository enrolments, CertificateService certificates,
            AccessPolicy access, WordingGuard wordingGuard, FileLinks files, Clock clock) {
        this.lessons = lessons;
        this.progress = progress;
        this.courses = courses;
        this.enrolments = enrolments;
        this.certificates = certificates;
        this.access = access;
        this.wordingGuard = wordingGuard;
        this.files = files;
        this.clock = clock;
    }

    // --- The team ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CourseLessonViews.AdminLesson> all(Long courseId) {
        access.requireAdmin();
        findCourse(courseId);
        return lessons.findByCourseIdOrderByPositionAscIdAsc(courseId).stream().map(this::adminView).toList();
    }

    @Transactional
    public CourseLessonViews.AdminLesson create(Long courseId, CourseLessonViews.LessonInput input) {
        access.requireAdmin();
        CourseLesson lesson = new CourseLesson(findCourse(courseId), input.position(), input.title(), input.body(),
                input.minutes());
        attach(lesson, input);
        return adminView(lessons.save(lesson));
    }

    /** A lesson that is changed goes back to a draft; learners keep what they have done. */
    @Transactional
    public CourseLessonViews.AdminLesson update(Long lessonId, CourseLessonViews.LessonInput input) {
        access.requireAdmin();
        CourseLesson lesson = find(lessonId);
        lesson.revise(input.position(), input.title(), input.body(), input.minutes());
        attach(lesson, input);
        return adminView(lesson);
    }

    @Transactional
    public CourseLessonViews.AdminLesson approve(Long lessonId) {
        CurrentUser me = access.requireAdmin();
        CourseLesson lesson = find(lessonId);
        wordingGuard.requireClean(lesson.wording());
        lesson.review(ContentStatus.APPROVED, me.email(), clock.instant());
        return adminView(lesson);
    }

    @Transactional
    public CourseLessonViews.AdminLesson reject(Long lessonId) {
        CurrentUser me = access.requireAdmin();
        CourseLesson lesson = find(lessonId);
        lesson.review(ContentStatus.REJECTED, me.email(), clock.instant());
        return adminView(lesson);
    }

    /** A lesson learners have done is kept, so their progress stands. */
    @Transactional
    public void delete(Long lessonId) {
        access.requireAdmin();
        CourseLesson lesson = find(lessonId);
        if (progress.countByLessonId(lessonId) > 0) {
            throw new IllegalStateException("Learners have done this lesson, so it is kept. You can still change it.");
        }
        lessons.delete(lesson);
    }

    // --- Learners ----------------------------------------------------------------------------------------------------

    /** A place on a recorded course, with its lessons. The learner whose place it is, or the team. */
    @Transactional(readOnly = true)
    public CourseLessonViews.Player player(Long enrolmentId) {
        Enrolment e = findRecorded(enrolmentId);
        access.requireCoursePlace(e.getUser().getId());
        return view(e);
    }

    /**
     * Marks a lesson done. Only the learner, and only for a lesson that is open: the web and the app offer it once the
     * lesson's film has played to the end. Doing the last lesson opens the quizzes, or gives the certificate of a
     * course that has none.
     */
    @Transactional
    public CourseLessonViews.Player complete(Long enrolmentId, Long lessonId) {
        Enrolment e = findRecorded(enrolmentId);
        access.requireOwnCoursePlace(e.getUser().getId());
        List<CourseLesson> approved = approved(e.getCourse());
        Set<Long> done = done(e);
        CourseLesson lesson = approved.stream().filter(l -> l.getId().equals(lessonId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        for (CourseLesson before : approved) {
            if (before.getId().equals(lessonId)) {
                break;
            }
            if (!done.contains(before.getId())) {
                throw new IllegalStateException("Finish lesson " + before.getPosition() + " first: the lessons go in order");
            }
        }
        if (!done.contains(lesson.getId())) {
            progress.saveAndFlush(new CourseLessonProgress(e.getId(), lesson.getId(), clock.instant()));
            certificates.issueIfComplete(e);
        }
        return view(e);
    }

    /** Whether the learner has started: once a lesson is done, a place is no longer theirs to cancel. */
    @Transactional(readOnly = true)
    public boolean started(Enrolment e) {
        return progress.countByEnrolmentId(e.getId()) > 0;
    }

    public int approvedLessons(Course course) {
        return (int) lessons.countByCourseIdAndStatus(course.getId(), ContentStatus.APPROVED);
    }

    public int approvedMinutes(Course course) {
        return approved(course).stream().mapToInt(CourseLesson::getMinutes).sum();
    }

    private CourseLessonViews.Player view(Enrolment e) {
        List<CourseLesson> approved = approved(e.getCourse());
        Set<Long> done = done(e);
        List<CourseLessonViews.Lesson> list = new ArrayList<>();
        boolean open = true;
        for (CourseLesson l : approved) {
            boolean isDone = done.contains(l.getId());
            list.add(new CourseLessonViews.Lesson(l.getId(), l.getPosition(), l.getTitle(), l.paragraphs(),
                    l.getMinutes(), l.hasFilm(), open ? files.link(l.getVideoKey(), l.getVideoUrl()) : null,
                    open ? files.download(l.getSlidesKey(), l.getSlidesUrl(), l.getTitle() + " slides") : null, open,
                    isDone));
            open = open && isDone;
        }
        int lessonsDone = (int) list.stream().filter(CourseLessonViews.Lesson::done).count();
        TrainingViews.QuizProgress quizzes = certificates.quizProgress(e);
        return new CourseLessonViews.Player(e.getId(), e.getCourse().getId(), e.getCourse().getTitle(),
                e.getUser().getDisplayName(), list, lessonsDone,
                approved.stream().mapToInt(CourseLesson::getMinutes).sum(),
                !approved.isEmpty() && lessonsDone == approved.size(), quizzes.total(), quizzes.passed(),
                e.getCertificateIssuedOn());
    }

    private void attach(CourseLesson lesson, CourseLessonViews.LessonInput input) {
        lesson.film(link(input.videoUrl(), input.videoKey(), "film"), files.requireKey(blankToNull(input.videoKey())));
        lesson.slides(link(input.slidesUrl(), input.slidesKey(), "slides"),
                files.requireKey(blankToNull(input.slidesKey())));
    }

    /** A pasted link must be https, and there is a link or an uploaded file, never both. */
    private static String link(String url, String key, String what) {
        String pasted = blankToNull(url);
        if (pasted == null) {
            return null;
        }
        if (blankToNull(key) != null) {
            throw new IllegalArgumentException("Give the " + what + " as a link or as an uploaded file, not both");
        }
        if (!pasted.startsWith("https://")) {
            throw new IllegalArgumentException("The " + what + " link must start with https://");
        }
        return pasted;
    }

    private CourseLessonViews.AdminLesson adminView(CourseLesson l) {
        return new CourseLessonViews.AdminLesson(l.getId(), l.getPosition(), l.getTitle(), l.getBody(),
                l.paragraphs(), l.getMinutes(), l.getStatus(), l.getReviewedBy(), l.getReviewedAt(),
                wordingGuard.check(l.wording()), files.link(l.getVideoKey(), l.getVideoUrl()), l.getVideoUrl(),
                l.getVideoKey() != null, files.link(l.getSlidesKey(), l.getSlidesUrl()), l.getSlidesUrl(),
                l.getSlidesKey() != null, l.getId() == null ? 0 : progress.countByLessonId(l.getId()));
    }

    private List<CourseLesson> approved(Course course) {
        return lessons.findByCourseIdAndStatusOrderByPositionAscIdAsc(course.getId(), ContentStatus.APPROVED);
    }

    private Set<Long> done(Enrolment e) {
        return progress.findByEnrolmentId(e.getId()).stream().map(CourseLessonProgress::getLessonId)
                .collect(Collectors.toSet());
    }

    private Enrolment findRecorded(Long enrolmentId) {
        Enrolment e = enrolments.findById(enrolmentId)
                .orElseThrow(() -> new NotFoundException("Enrolment", enrolmentId));
        if (!e.isRecorded()) {
            throw new NotFoundException("Enrolment", enrolmentId);
        }
        return e;
    }

    private Course findCourse(Long id) {
        return courses.findById(id).orElseThrow(() -> new NotFoundException("Course", id));
    }

    private CourseLesson find(Long id) {
        return lessons.findById(id).orElseThrow(() -> new NotFoundException("Lesson", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
