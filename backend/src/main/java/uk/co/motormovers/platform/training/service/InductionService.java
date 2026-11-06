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
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.school.service.LicenceService;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.InductionAudience;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.model.InductionProgress;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.repository.InductionProgressRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The inductions. School staff work through the approved lessons of the one-hour induction at their own pace; once
 * the last one is done it is complete for good and the daily sessions unlock. Parents on Motor Movers at Home do the
 * same with the short parent induction, through HomeService, before their first home session. Lessons added later
 * lock nobody out. The staff lessons' films are paid content, for the team and staff at licensed schools; everyone
 * else who takes the induction gets the same lessons without the film.
 */
@Service
public class InductionService {

    private final InductionLessonRepository lessons;
    private final InductionProgressRepository progress;
    private final AppUserRepository users;
    private final CurrentUserResolver currentUser;
    private final Clock clock;
    private final FileLinks files;
    private final LicenceService licences;

    public InductionService(InductionLessonRepository lessons, InductionProgressRepository progress,
            AppUserRepository users, CurrentUserResolver currentUser, Clock clock, FileLinks files,
            LicenceService licences) {
        this.lessons = lessons;
        this.progress = progress;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
        this.files = files;
        this.licences = licences;
    }

    @Transactional(readOnly = true)
    public TrainingViews.InductionView mine() {
        return view(requireStaff(), InductionAudience.STAFF);
    }

    @Transactional
    public TrainingViews.InductionView completeLesson(Long lessonId) {
        return complete(requireStaff(), InductionAudience.STAFF, lessonId);
    }

    /** The parent induction for the signed-in parent. */
    @Transactional(readOnly = true)
    public TrainingViews.InductionView mineAsParent(CurrentUser parent) {
        return view(parent, InductionAudience.PARENT);
    }

    @Transactional
    public TrainingViews.InductionView completeParentLesson(CurrentUser parent, Long lessonId) {
        return complete(parent, InductionAudience.PARENT, lessonId);
    }

    private TrainingViews.InductionView view(CurrentUser me, InductionAudience audience) {
        Set<Long> done = progress.findByIdUserId(me.id()).stream()
                .map(InductionProgress::getLessonId).collect(Collectors.toSet());
        boolean mayWatch = audience != InductionAudience.STAFF || mayWatchStaffFilms(me);
        List<TrainingViews.LessonView> list = lessons
                .findByAudienceAndStatusOrderByPosition(audience, ContentStatus.APPROVED).stream()
                .map(l -> TrainingViews.LessonView.of(l, files.link(l.getVideoKey(), l.getVideoUrl()), mayWatch,
                        done.contains(l.getId())))
                .toList();
        return new TrainingViews.InductionView(list, list.stream().mapToInt(TrainingViews.LessonView::minutes).sum(),
                (int) list.stream().filter(TrainingViews.LessonView::done).count(),
                users.findById(me.id()).map(AppUser::getInductionCompletedOn).orElse(null));
    }

    private boolean mayWatchStaffFilms(CurrentUser me) {
        return me.is(Role.ADMIN) || ((me.is(Role.TEACHER) || me.is(Role.HEAD)) && me.schoolId() != null
                && licences.hasLicence(me.schoolId(), LocalDate.now(clock)));
    }

    private TrainingViews.InductionView complete(CurrentUser me, InductionAudience audience, Long lessonId) {
        InductionLesson lesson = lessons.findById(lessonId)
                .filter(l -> l.getStatus() == ContentStatus.APPROVED && l.getAudience() == audience)
                .orElseThrow(() -> new NotFoundException("Induction lesson", lessonId));
        InductionProgress.Key key = new InductionProgress.Key(me.id(), lesson.getId());
        if (!progress.existsById(key)) {
            progress.save(new InductionProgress(me.id(), lesson.getId()));
        }
        Set<Long> done = progress.findByIdUserId(me.id()).stream()
                .map(InductionProgress::getLessonId).collect(Collectors.toSet());
        boolean allDone = lessons.findByAudienceAndStatusOrderByPosition(audience, ContentStatus.APPROVED).stream()
                .allMatch(l -> done.contains(l.getId()));
        if (allDone) {
            users.findById(me.id()).ifPresent(u -> u.completeInduction(LocalDate.now(clock)));
        }
        return view(me, audience);
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
