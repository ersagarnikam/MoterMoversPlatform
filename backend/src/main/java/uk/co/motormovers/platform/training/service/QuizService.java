package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.QuizViews;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseQuiz;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.model.QuizAttempt;
import uk.co.motormovers.platform.training.model.QuizQuestion;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.CourseQuizRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.QuizAttemptRepository;
import uk.co.motormovers.platform.training.repository.QuizQuestionRepository;
import uk.co.motormovers.platform.training.repository.SessionAttendanceRepository;

/**
 * Short quizzes between a course's live sessions. The team writes them (the wording check runs on save, as for
 * courses); learners take them once the session they follow has started (or they are marked present at it), as
 * often as they like. The answers never
 * leave the server: learners are told their score and which questions to look at again.
 */
@Service
public class QuizService {

    private final CourseRepository courses;
    private final CourseQuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final QuizAttemptRepository attempts;
    private final EnrolmentRepository enrolments;
    private final CohortSessionRepository sessions;
    private final SessionAttendanceRepository attendance;
    private final CertificateService certificates;
    private final WordingGuard wordingGuard;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public QuizService(CourseRepository courses, CourseQuizRepository quizzes, QuizQuestionRepository questions,
            QuizAttemptRepository attempts, EnrolmentRepository enrolments, CohortSessionRepository sessions,
            SessionAttendanceRepository attendance, CertificateService certificates, WordingGuard wordingGuard, CurrentUserResolver currentUser, Clock clock) {
        this.courses = courses;
        this.quizzes = quizzes;
        this.questions = questions;
        this.attempts = attempts;
        this.enrolments = enrolments;
        this.sessions = sessions;
        this.attendance = attendance;
        this.certificates = certificates;
        this.wordingGuard = wordingGuard;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // The team

    @Transactional(readOnly = true)
    public List<QuizViews.QuizAdminView> forCourse(Long courseId) {
        findCourse(courseId);
        return quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(courseId).stream().map(this::adminView).toList();
    }

    @Transactional
    public QuizViews.QuizAdminView create(Long courseId, QuizViews.QuizInput input) {
        Course course = findCourse(courseId);
        check(course, input);
        CourseQuiz quiz = quizzes.save(new CourseQuiz(course, input.afterSession(), input.title().strip(),
                input.passPercent()));
        saveQuestions(quiz, input);
        return adminView(quiz);
    }

    /** Replaces the quiz and its questions. Scores already earned stand. */
    @Transactional
    public QuizViews.QuizAdminView update(Long quizId, QuizViews.QuizInput input) {
        CourseQuiz quiz = findQuiz(quizId);
        check(quiz.getCourse(), input);
        quiz.revise(input.afterSession(), input.title().strip(), input.passPercent());
        questions.deleteByQuizId(quizId);
        questions.flush();
        saveQuestions(quiz, input);
        return adminView(quiz);
    }

    /** A quiz learners have already taken is kept, so their results stand. */
    @Transactional
    public void delete(Long quizId) {
        CourseQuiz quiz = findQuiz(quizId);
        if (attempts.countByQuizId(quizId) > 0) {
            throw new IllegalStateException("Learners have taken this quiz, so it is kept. You can still change it.");
        }
        questions.deleteByQuizId(quizId);
        quizzes.delete(quiz);
    }

    // Learners

    /** The quizzes of a learner's course, with their best score. The learner, the team or the cohort's trainer. */
    @Transactional(readOnly = true)
    public List<QuizViews.QuizSummary> forEnrolment(Long enrolmentId) {
        Enrolment e = findEnrolment(enrolmentId);
        requireLearnerOrHost(e);
        List<QuizAttempt> taken = attempts.findByEnrolmentId(enrolmentId);
        return quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(e.getCohort().getCourse().getId()).stream()
                .map(q -> {
                    List<QuizAttempt> mine = taken.stream().filter(a -> a.getQuizId().equals(q.getId())).toList();
                    Integer best = mine.stream().map(QuizAttempt::getScorePercent).max(Integer::compare).orElse(null);
                    return new QuizViews.QuizSummary(q.getId(), q.getAfterSession(), q.getTitle(),
                            (int) questions.countByQuizId(q.getId()), q.getPassPercent(), isOpen(e, q), best,
                            mine.stream().anyMatch(QuizAttempt::isPassed));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizViews.QuizPaper paper(Long enrolmentId, Long quizId) {
        Enrolment e = requireOwn(enrolmentId);
        CourseQuiz quiz = quizFor(e, quizId);
        List<QuizQuestion> list = questions.findByQuizIdOrderByPosition(quizId);
        List<QuizViews.PaperQuestion> paper = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            paper.add(new QuizViews.PaperQuestion(i + 1, list.get(i).getText(), list.get(i).options()));
        }
        return new QuizViews.QuizPaper(quiz.getId(), quiz.getTitle(), quiz.getPassPercent(), paper);
    }

    /** Marks an attempt, saves the score, and issues the certificate if this completed the course. */
    @Transactional
    public QuizViews.AttemptResult attempt(Long enrolmentId, Long quizId, List<Integer> answers) {
        Enrolment e = requireOwn(enrolmentId);
        CourseQuiz quiz = quizFor(e, quizId);
        List<QuizQuestion> list = questions.findByQuizIdOrderByPosition(quizId);
        if (answers.size() != list.size()) {
            throw new IllegalArgumentException("Answer every question, then send your answers");
        }
        List<Integer> wrong = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Integer answer = answers.get(i);
            if (answer == null || answer != list.get(i).getCorrectOption()) {
                wrong.add(i + 1);
            }
        }
        int correct = list.size() - wrong.size();
        int score = Math.round(correct * 100f / list.size());
        boolean passed = score >= quiz.getPassPercent();
        attempts.saveAndFlush(new QuizAttempt(quizId, enrolmentId, score, passed, clock.instant()));
        boolean issued = passed && certificates.issueIfComplete(e);
        return new QuizViews.AttemptResult(score, passed, correct, list.size(), wrong, issued);
    }

    /** Once the session it follows has started, or the learner has been marked present at it. */
    private boolean isOpen(Enrolment e, CourseQuiz quiz) {
        if (attendance.attendedNumbers(e.getId()).contains(quiz.getAfterSession())) {
            return true;
        }
        Instant now = clock.instant();
        return sessions.findByCohortIdOrderByNumber(e.getCohort().getId()).stream()
                .filter(s -> s.getNumber() == quiz.getAfterSession())
                .findFirst()
                .map(s -> !now.isBefore(s.getStartsAt()))
                .orElse(true);
    }

    private CourseQuiz quizFor(Enrolment e, Long quizId) {
        CourseQuiz quiz = findQuiz(quizId);
        if (!quiz.getCourse().getId().equals(e.getCohort().getCourse().getId())) {
            throw new NotFoundException("Quiz", quizId);
        }
        if (!isOpen(e, quiz)) {
            throw new IllegalStateException("This quiz opens when live session " + quiz.getAfterSession() + " starts");
        }
        return quiz;
    }

    private void check(Course course, QuizViews.QuizInput input) {
        if (input.afterSession() > course.getLiveSessions()) {
            throw new IllegalArgumentException("This course has " + course.getLiveSessions() + " live sessions");
        }
        for (QuizViews.QuestionInput q : input.questions()) {
            if (q.correctOption() < 0 || q.correctOption() >= q.options().size()) {
                throw new IllegalArgumentException("Choose the right answer for \"" + q.text().strip() + "\"");
            }
        }
        wordingGuard.requireClean(Stream.concat(Stream.of(input.title()), input.questions().stream()
                .flatMap(q -> Stream.concat(Stream.of(q.text()), q.options().stream()))).toList());
    }

    private void saveQuestions(CourseQuiz quiz, QuizViews.QuizInput input) {
        for (int i = 0; i < input.questions().size(); i++) {
            QuizViews.QuestionInput q = input.questions().get(i);
            questions.save(new QuizQuestion(quiz, i + 1, q.text().strip(),
                    q.options().stream().map(o -> o.strip().replace('\n', ' ')).toList(), q.correctOption()));
        }
    }

    private QuizViews.QuizAdminView adminView(CourseQuiz q) {
        return new QuizViews.QuizAdminView(q.getId(), q.getAfterSession(), q.getTitle(), q.getPassPercent(),
                questions.findByQuizIdOrderByPosition(q.getId()).stream()
                        .map(x -> new QuizViews.QuestionInput(x.getText(), x.options(), x.getCorrectOption()))
                        .toList(),
                attempts.countByQuizId(q.getId()));
    }

    private Enrolment requireOwn(Long enrolmentId) {
        Enrolment e = findEnrolment(enrolmentId);
        if (!e.getUser().getId().equals(currentUser.current().id())) {
            throw new AccessDeniedException("Only the learner takes their quizzes");
        }
        return e;
    }

    private void requireLearnerOrHost(Enrolment e) {
        CurrentUser me = currentUser.current();
        if (!e.getUser().getId().equals(me.id()) && !LiveSessionService.canHost(me, e.getCohort())) {
            throw new AccessDeniedException("You do not have access to this learner's quizzes");
        }
    }

    private Course findCourse(Long id) {
        return courses.findById(id).orElseThrow(() -> new NotFoundException("Course", id));
    }

    private CourseQuiz findQuiz(Long id) {
        return quizzes.findById(id).orElseThrow(() -> new NotFoundException("Quiz", id));
    }

    private Enrolment findEnrolment(Long id) {
        return enrolments.findById(id).orElseThrow(() -> new NotFoundException("Enrolment", id));
    }
}
