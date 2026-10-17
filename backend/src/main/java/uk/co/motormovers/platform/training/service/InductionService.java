package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.model.InductionProgress;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.repository.InductionProgressRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The one-hour induction. School staff work through the approved lessons at their own pace; once the
 * last one is done the induction is complete for good and the daily sessions unlock.
 */
@Service
public class InductionService {

    private final InductionLessonRepository lessons;
    private final InductionProgressRepository progress;
    private final AppUserRepository users;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public InductionService(InductionLessonRepository lessons, InductionProgressRepository progress,
            AppUserRepository users, CurrentUserResolver currentUser, Clock clock) {
        this.lessons = lessons;
        this.progress = progress;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrainingViews.InductionView mine() {
        CurrentUser me = requireStaff();
        Set<Long> done = progress.findByIdUserId(me.id()).stream()
                .map(InductionProgress::getLessonId).collect(Collectors.toSet());
        List<TrainingViews.LessonView> list = lessons.findByStatusOrderByPosition(ContentStatus.APPROVED).stream()
                .map(l -> TrainingViews.LessonView.of(l, done.contains(l.getId())))
                .toList();
        return new TrainingViews.InductionView(list, list.stream().mapToInt(TrainingViews.LessonView::minutes).sum(),
                (int) list.stream().filter(TrainingViews.LessonView::done).count(),
                users.findById(me.id()).map(AppUser::getInductionCompletedOn).orElse(null));
    }

    @Transactional
    public TrainingViews.InductionView completeLesson(Long lessonId) {
        CurrentUser me = requireStaff();
        InductionLesson lesson = lessons.findById(lessonId)
                .filter(l -> l.getStatus() == ContentStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Induction lesson", lessonId));
        InductionProgress.Key key = new InductionProgress.Key(me.id(), lesson.getId());
        if (!progress.existsById(key)) {
            progress.save(new InductionProgress(me.id(), lesson.getId()));
        }
        Set<Long> done = progress.findByIdUserId(me.id()).stream()
                .map(InductionProgress::getLessonId).collect(Collectors.toSet());
        boolean allDone = lessons.findByStatusOrderByPosition(ContentStatus.APPROVED).stream()
                .allMatch(l -> done.contains(l.getId()));
        if (allDone) {
            users.findById(me.id()).ifPresent(u -> u.completeInduction(LocalDate.now(clock)));
        }
        return mine();
    }

    /** The platform team runs no sessions of its own, so it is never held back by the induction. */
    @Transactional(readOnly = true)
    public boolean isComplete(CurrentUser user) {
        return user.is(Role.ADMIN)
                || users.findById(user.id()).map(u -> u.getInductionCompletedOn() != null).orElse(false);
    }

    public void requireComplete(CurrentUser user) {
        if (!isComplete(user)) {
            throw new IllegalStateException(
                    "Complete the one-hour Motor Movers induction (under Training) before running sessions");
        }
    }

    private CurrentUser requireStaff() {
        CurrentUser me = currentUser.current();
        if (me.is(Role.PARENT)) {
            throw new AccessDeniedException("The induction is for school staff");
        }
        return me;
    }
}
