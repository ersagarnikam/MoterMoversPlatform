package uk.co.motormovers.platform.family.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.assessment.dto.AssessmentViews;
import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.assessment.repository.ExerciseScoreRepository;
import uk.co.motormovers.platform.assessment.service.ParentProgress;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.model.ChildAccessLog;
import uk.co.motormovers.platform.child.repository.ChildAccessLogRepository;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.classroom.repository.DailySessionRecordRepository;
import uk.co.motormovers.platform.classroom.service.ClassroomService;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.util.SchoolDays;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.GuidanceArticle;
import uk.co.motormovers.platform.content.model.HomeActivity;
import uk.co.motormovers.platform.content.repository.GuidanceArticleRepository;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.family.dto.FamilyViews;
import uk.co.motormovers.platform.family.model.HomeSessionRecord;
import uk.co.motormovers.platform.family.model.ParentInvite;
import uk.co.motormovers.platform.family.model.ParentLink;
import uk.co.motormovers.platform.family.repository.HomeSessionRecordRepository;
import uk.co.motormovers.platform.family.repository.ParentInviteRepository;
import uk.co.motormovers.platform.family.repository.ParentLinkRepository;
import uk.co.motormovers.platform.family.util.InviteCodes;
import uk.co.motormovers.platform.screening.dto.ScreeningViews;
import uk.co.motormovers.platform.screening.service.ScreeningService;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/** The parent app: linked children and their progress, home activities, and guidance. */
@Service
public class FamilyService {

    /** Days shown on the home activity counter. Home includes weekends, so these are calendar days. */
    public static final int HOME_COUNTER_DAYS = 14;

    /** How far back a home session can be recorded, e.g. one logged offline and sent days later. */
    static final int MAX_BACKDATE_DAYS = 30;

    /** Same message for every bad code, so the response does not reveal which codes exist. */
    static final String INVALID_CODE = "That code is not valid or has expired. Ask your child's school for a new one.";

    private final ParentLinkRepository links;
    private final ParentInviteRepository invites;
    private final ChildRepository children;
    private final ChildAccessLogRepository accessLog;
    private final HomeActivityRepository activities;
    private final HomeSessionRecordRepository homeRecords;
    private final GuidanceArticleRepository guidance;
    private final DailySessionRecordRepository classRecords;
    private final AppUserRepository users;
    private final ScreeningService screening;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final Clock clock;
    private final ParentProgress parentProgress;
    private final ExerciseScoreRepository scores;

    public FamilyService(ParentLinkRepository links, ParentInviteRepository invites, ChildRepository children,
            ChildAccessLogRepository accessLog, HomeActivityRepository activities,
            HomeSessionRecordRepository homeRecords, GuidanceArticleRepository guidance,
            DailySessionRecordRepository classRecords, AppUserRepository users, ScreeningService screening,
            AccessPolicy access, CurrentUserResolver currentUser, Clock clock, ParentProgress parentProgress,
            ExerciseScoreRepository scores) {
        this.links = links;
        this.invites = invites;
        this.children = children;
        this.accessLog = accessLog;
        this.activities = activities;
        this.homeRecords = homeRecords;
        this.guidance = guidance;
        this.classRecords = classRecords;
        this.users = users;
        this.screening = screening;
        this.access = access;
        this.currentUser = currentUser;
        this.clock = clock;
        this.parentProgress = parentProgress;
        this.scores = scores;
    }

    /** Children this parent may currently see. Links stay dormant while the school has parent links off. */
    @Transactional(readOnly = true)
    public List<FamilyViews.LinkedChild> children() {
        return visibleLinks(currentUser.current().id()).stream().map(FamilyViews.LinkedChild::of).toList();
    }

    @Transactional
    public FamilyViews.LinkedChild redeem(String typedCode) {
        CurrentUser me = currentUser.current();
        LocalDate today = LocalDate.now(clock);
        ParentInvite invite = invites.findByCode(InviteCodes.normalise(typedCode))
                .filter(i -> i.isRedeemable(today))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CODE));
        Child child = invite.getChild();
        if (child.getLeftOn() != null || !child.getSchoolClass().getSchool().isParentLinksEnabled()) {
            throw new IllegalArgumentException(INVALID_CODE);
        }
        invite.redeem(me.id());
        ParentLink link = links.findByParentIdAndChildId(me.id(), child.getId())
                .orElseGet(() -> links.save(new ParentLink(users.getReferenceById(me.id()), child)));
        accessLog.save(new ChildAccessLog(child.getId(), child.getSchoolClass().getSchool().getId(), me.email(),
                "PARENT_LINKED"));
        return FamilyViews.LinkedChild.of(link);
    }

    /** The parent removes a child from their app. The school can issue a new code later. */
    @Transactional
    public void unlink(Long childId) {
        CurrentUser me = currentUser.current();
        ParentLink link = links.findByParentIdAndChildId(me.id(), childId)
                .orElseThrow(() -> new NotFoundException("Linked child", childId));
        Child child = link.getChild();
        links.delete(link);
        accessLog.save(new ChildAccessLog(child.getId(), child.getSchoolClass().getSchool().getId(), me.email(),
                "PARENT_UNLINKED"));
    }

    /**
     * The parent's view of progress: stage, the school's assessments in plain words (never the scores or the
     * extra-support flag), and the programme bands. Every view is logged for the school.
     */
    @Transactional
    public FamilyViews.Progress progress(Long childId) {
        Child child = children.findById(childId).orElseThrow(() -> new NotFoundException("Child", childId));
        CurrentUser me = access.requireParentOf(child);
        if (child.isAtHome()) {
            // A home child's progress is at /api/home/children/{id}.
            throw new NotFoundException("Linked child", childId);
        }
        accessLog.save(new ChildAccessLog(child.getId(), child.getSchoolClass().getSchool().getId(), me.email(),
                "PARENT_VIEW_PROGRESS"));
        ParentLink link = links.findByParentIdAndChildId(me.id(), childId).orElseThrow();

        ScreeningViews.Readiness readiness = screening.readiness(childId);
        List<FamilyViews.AreaBand> areas = readiness.areas().stream()
                .map(a -> new FamilyViews.AreaBand(a.area(), a.label(), a.band(), a.bandLabel()))
                .toList();
        List<LocalDate> screenings = readiness.history().stream().map(ScreeningViews.RoundPoint::screenedOn).toList();

        List<LocalDate> window = SchoolDays.lastSchoolDays(LocalDate.now(clock), ClassroomService.COUNTER_DAYS);
        int classSessions = classRecords.findBySchoolClassIdInAndSessionDateBetween(
                List.of(child.getSchoolClass().getId()), window.getFirst(), window.getLast()).size();

        // Exercises of the child's own colour if the school has rated them there, else of the class's colour.
        ColourStage exerciseStage = null;
        List<AssessmentViews.ExerciseProgress> exercises = List.of();
        for (ColourStage stage : Stream.of(child.getCurrentStage(), child.getSchoolClass().getStage())
                .filter(Objects::nonNull).distinct().toList()) {
            List<ExerciseScore> theirs = scores.findByChildIdAndExerciseStage(childId, stage);
            if (!theirs.isEmpty()) {
                exerciseStage = stage;
                exercises = parentProgress.exercises(stage, theirs);
                break;
            }
        }
        return new FamilyViews.Progress(FamilyViews.LinkedChild.of(link), ScreeningViews.NOT_A_DIAGNOSIS,
                readiness.screened(), areas, screenings, classSessions, ClassroomService.COUNTER_DAYS,
                parentProgress.charts(childId, LocalDate.now(clock)), exerciseStage,
                exerciseStage == null ? null : exerciseStage.displayName(), exercises);
    }

    /**
     * Home activities for a stage: the one to do next, the full set, and the 14-day counter. The stage
     * comes from the request, else the linked child's current stage, else the first stage.
     */
    @Transactional(readOnly = true)
    public FamilyViews.Home home(ColourStage requestedStage, Long childId) {
        CurrentUser me = currentUser.current();
        ColourStage stage = requestedStage;
        if (stage == null && childId != null) {
            Child child = children.findById(childId).orElseThrow(() -> new NotFoundException("Child", childId));
            access.requireParentOf(child);
            stage = child.getCurrentStage();
        }
        if (stage == null) {
            stage = visibleLinks(me.id()).stream().map(l -> l.getChild().getCurrentStage())
                    .filter(Objects::nonNull).findFirst().orElse(ColourStage.RED);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(HOME_COUNTER_DAYS - 1);
        List<HomeSessionRecord> recent = homeRecords.findByParentIdAndDoneOnBetweenOrderByIdAsc(me.id(), from, today);
        Set<LocalDate> done = new HashSet<>();
        recent.forEach(r -> done.add(r.getDoneOn()));
        List<Boolean> grid = from.datesUntil(today.plusDays(1)).map(done::contains).toList();

        List<HomeActivity> sequence = activities.findByStageAndStatusOrderByPosition(stage, ContentStatus.APPROVED);
        // Once today's activity is done, keep showing it rather than jumping ahead to tomorrow's.
        final ColourStage s = stage;
        Optional<HomeActivity> next = recent.stream()
                .filter(r -> r.getDoneOn().equals(today) && r.getStage() == s)
                .reduce((first, second) -> second)
                .map(HomeSessionRecord::getActivity)
                .or(() -> sequence.isEmpty() ? Optional.empty()
                        : Optional.of(sequence.get((int) (homeRecords.countByParentIdAndStage(me.id(), s)
                                % sequence.size()))));

        return new FamilyViews.Home(stage, stage.displayName(), next.map(FamilyViews.ActivityCard::of).orElse(null),
                sequence.stream().map(FamilyViews.ActivityCard::of).toList(), done.contains(today),
                (int) grid.stream().filter(Boolean::booleanValue).count(), HOME_COUNTER_DAYS, grid,
                homeRecords.countByParentId(me.id()));
    }

    @Transactional(readOnly = true)
    public FamilyViews.ActivityCard activity(Long id) {
        return activities.findById(id)
                .filter(a -> a.getStatus() == ContentStatus.APPROVED)
                .map(FamilyViews.ActivityCard::of)
                .orElseThrow(() -> new NotFoundException("Home activity", id));
    }

    /** Accepts a batch of home sessions, typically queued on the phone while offline. Safe to resend. */
    @Transactional
    public FamilyViews.SubmitResult recordHome(List<FamilyViews.HomeEntry> entries) {
        CurrentUser me = currentUser.current();
        LocalDate today = LocalDate.now(clock);
        int saved = 0;
        int duplicates = 0;
        for (FamilyViews.HomeEntry e : entries) {
            if (e.clientId() == null || e.clientId().isBlank() || e.activityId() == null || e.doneOn() == null) {
                throw new IllegalArgumentException("Each entry needs a clientId, activityId and doneOn");
            }
            if (homeRecords.existsByClientId(e.clientId())) {
                duplicates++;
                continue;
            }
            // One day of slack either way for phones in other time zones.
            if (e.doneOn().isAfter(today.plusDays(1)) || e.doneOn().isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
                throw new IllegalArgumentException("Home sessions can be recorded for the last "
                        + MAX_BACKDATE_DAYS + " days only");
            }
            HomeActivity activity = activities.findById(e.activityId())
                    .filter(a -> a.getStatus() == ContentStatus.APPROVED)
                    .orElseThrow(() -> new NotFoundException("Home activity", e.activityId()));
            if (e.childId() != null) {
                access.requireParentOf(children.findById(e.childId())
                        .orElseThrow(() -> new NotFoundException("Child", e.childId())));
            }
            homeRecords.save(new HomeSessionRecord(e.clientId(), me.id(), e.childId(), activity, e.doneOn()));
            saved++;
        }
        return new FamilyViews.SubmitResult(saved, duplicates);
    }

    /** Approved guidance, optionally narrowed to one stage (articles for every stage are always included). */
    @Transactional(readOnly = true)
    public List<FamilyViews.ArticleSummary> guidance(ColourStage stage) {
        return guidance.findByStatusOrderByIdDesc(ContentStatus.APPROVED).stream()
                .filter(a -> stage == null || a.getStage() == null || a.getStage() == stage)
                .map(FamilyViews.ArticleSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public FamilyViews.Article article(Long id) {
        GuidanceArticle article = guidance.findById(id)
                .filter(a -> a.getStatus() == ContentStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Article", id));
        return FamilyViews.Article.of(article);
    }

    private List<ParentLink> visibleLinks(Long parentId) {
        return links.findByParentIdOrderByLinkedAt(parentId).stream()
                .filter(l -> l.getChild().getLeftOn() == null
                        && l.getChild().getSchoolClass().getSchool().isParentLinksEnabled())
                .toList();
    }
}
