package uk.co.motormovers.platform.home.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.assessment.dto.AssessmentViews;
import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;
import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.ExerciseScoreRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneAchievementRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.assessment.util.ExtraSupportRule;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.family.dto.FamilyViews;
import uk.co.motormovers.platform.family.service.FamilyService;
import uk.co.motormovers.platform.family.util.InviteCodes;
import uk.co.motormovers.platform.home.dto.HomeViews;
import uk.co.motormovers.platform.home.model.Family;
import uk.co.motormovers.platform.home.model.FamilyInvite;
import uk.co.motormovers.platform.home.model.FamilyMember;
import uk.co.motormovers.platform.home.model.HomeProgrammeSession;
import uk.co.motormovers.platform.home.repository.FamilyInviteRepository;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.home.repository.FamilyRepository;
import uk.co.motormovers.platform.home.repository.HomeProgrammeSessionRepository;
import uk.co.motormovers.platform.home.util.HomeRules;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Motor Movers at Home (docs/home-programme-design.md): a family's own children, not linked to a school. The parent
 * adds a child with an age in months, does a starting check from Pat's charts that suggests a colour, then a daily
 * session from Pat's exercises, rating each exercise the first time and at the end of each module. A second carer
 * can join with a code, and a school can be linked later. Switched on with {@code platform.home-programme}.
 */
@Service
public class HomeService {

    public static final int MAX_CHILDREN = 6;

    static final int MAX_NAME = 30;

    /** Ages the app accepts: the programme covers 6 months to 8 years; a little either side is allowed. */
    static final int MAX_AGE_MONTHS = 120;

    /** Days a carer's code works. */
    static final int INVITE_DAYS = 14;

    /** Days on the counter. Home includes weekends, so these are calendar days. */
    public static final int COUNTER_DAYS = 20;

    static final int MAX_BACKDATE_DAYS = 30;

    static final String NOT_AVAILABLE = "Motor Movers at Home is not available yet";

    static final String INVALID_CODE = "That code is not valid or has expired. Ask for a new one.";

    private final boolean enabled;
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final FamilyInviteRepository invites;
    private final HomeProgrammeSessionRepository sessions;
    private final ChildRepository children;
    private final MilestoneRepository milestones;
    private final MilestoneAchievementRepository achievements;
    private final ExerciseRepository exercises;
    private final ExerciseScoreRepository scores;
    private final FamilyService familyService;
    private final AppUserRepository users;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public HomeService(@Value("${platform.home-programme:false}") boolean enabled, FamilyRepository families,
            FamilyMemberRepository members, FamilyInviteRepository invites, HomeProgrammeSessionRepository sessions,
            ChildRepository children, MilestoneRepository milestones, MilestoneAchievementRepository achievements,
            ExerciseRepository exercises, ExerciseScoreRepository scores, FamilyService familyService,
            AppUserRepository users, AccessPolicy access, CurrentUserResolver currentUser, Clock clock) {
        this.enabled = enabled;
        this.families = families;
        this.members = members;
        this.invites = invites;
        this.sessions = sessions;
        this.children = children;
        this.milestones = milestones;
        this.achievements = achievements;
        this.exercises = exercises;
        this.scores = scores;
        this.familyService = familyService;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // --- The family -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HomeViews.Overview overview() {
        if (!enabled) {
            return new HomeViews.Overview(false, List.of(), List.of(), false, MAX_CHILDREN);
        }
        CurrentUser me = currentUser.current();
        Optional<FamilyMember> mine = members.findByUserId(me.id());
        if (mine.isEmpty()) {
            return new HomeViews.Overview(true, List.of(), List.of(), true, MAX_CHILDREN);
        }
        Long familyId = mine.get().getFamilyId();
        LocalDate today = today();
        List<HomeViews.HomeChild> kids = children.findByFamilyIdOrderById(familyId).stream()
                .map(c -> homeChild(c, today)).toList();
        Map<Long, AppUser> people = users.findAllById(members.findByFamilyIdOrderByJoinedAt(familyId).stream()
                .map(FamilyMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<HomeViews.Member> list = members.findByFamilyIdOrderByJoinedAt(familyId).stream()
                .map(m -> new HomeViews.Member(m.getUserId(),
                        Optional.ofNullable(people.get(m.getUserId())).map(AppUser::getDisplayName).orElse(""),
                        m.getRole() == FamilyMember.Role.OWNER, m.getUserId().equals(me.id())))
                .toList();
        return new HomeViews.Overview(true, kids, list, mine.get().getRole() == FamilyMember.Role.OWNER,
                MAX_CHILDREN);
    }

    /** Adds a child to the parent's family, starting the family if this is their first. */
    @Transactional
    public HomeViews.HomeChild addChild(HomeViews.ChildIn in) {
        CurrentUser me = requireParent();
        String name = name(in);
        int age = age(in);
        Long familyId = members.findByUserId(me.id()).map(FamilyMember::getFamilyId).orElseGet(() -> {
            Family family = families.save(new Family());
            members.save(new FamilyMember(family.getId(), me.id(), FamilyMember.Role.OWNER));
            return family.getId();
        });
        if (children.findByFamilyIdOrderById(familyId).size() >= MAX_CHILDREN) {
            throw new IllegalArgumentException("A family can add up to " + MAX_CHILDREN + " children");
        }
        LocalDate today = today();
        return homeChild(children.save(Child.atHome(familyId, name, age, today)), today);
    }

    @Transactional
    public HomeViews.HomeChild updateChild(Long childId, HomeViews.ChildIn in) {
        Child child = homeChild(childId);
        child.rename(name(in));
        child.setAge(age(in), today());
        return homeChild(child, today());
    }

    /** Deletes a home child and everything recorded for them (charts, ratings, sessions). */
    @Transactional
    public void deleteChild(Long childId) {
        children.delete(homeChild(childId));
    }

    /** A code for a second parent or carer, so they can share the family's children. */
    @Transactional
    public HomeViews.Invite invite() {
        CurrentUser me = requireParent();
        FamilyMember mine = members.findByUserId(me.id())
                .orElseThrow(() -> new IllegalArgumentException("Add a child before inviting someone to share"));
        String code;
        do {
            code = InviteCodes.generate();
        } while (invites.findByCode(code).isPresent());
        FamilyInvite invite = invites.save(new FamilyInvite(mine.getFamilyId(), code, me.id(),
                today().plusDays(INVITE_DAYS)));
        return new HomeViews.Invite(InviteCodes.format(invite.getCode()), invite.getExpiresOn());
    }

    /** Joins the family whose code this is. Someone who already has their own home children cannot. */
    @Transactional
    public HomeViews.Overview join(String typedCode) {
        CurrentUser me = requireParent();
        FamilyInvite invite = invites.findByCode(InviteCodes.normalise(typedCode))
                .filter(i -> i.isRedeemable(today()))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CODE));
        Optional<FamilyMember> current = members.findByUserId(me.id());
        if (current.isPresent()) {
            if (current.get().getFamilyId().equals(invite.getFamilyId())) {
                return overview();
            }
            if (!children.findByFamilyIdOrderById(current.get().getFamilyId()).isEmpty()) {
                throw new IllegalArgumentException("You already have children in the app. Ask the other parent or "
                        + "carer to join you instead.");
            }
            leave(current.get());
        }
        invite.redeem(me.id());
        members.save(new FamilyMember(invite.getFamilyId(), me.id(), FamilyMember.Role.CARER));
        return overview();
    }

    /**
     * Removes someone from the family: yourself, or (the owner) a carer. When the last member leaves, the family and
     * its children's records are deleted.
     */
    @Transactional
    public void removeMember(Long userId) {
        CurrentUser me = requireParent();
        FamilyMember mine = members.findByUserId(me.id()).orElseThrow(() -> new NotFoundException("Family", me.id()));
        FamilyMember them = members.findByUserId(userId).filter(m -> m.getFamilyId().equals(mine.getFamilyId()))
                .orElseThrow(() -> new NotFoundException("Family member", userId));
        if (!userId.equals(me.id()) && mine.getRole() != FamilyMember.Role.OWNER) {
            throw new AccessDeniedException("Only the person who added the children can remove others");
        }
        leave(them);
    }

    private void leave(FamilyMember member) {
        Long familyId = member.getFamilyId();
        members.delete(member);
        members.flush();
        List<FamilyMember> rest = members.findByFamilyIdOrderByJoinedAt(familyId);
        if (rest.isEmpty()) {
            families.deleteById(familyId);
        } else if (member.getRole() == FamilyMember.Role.OWNER) {
            // The longest-standing carer takes over.
            FamilyMember next = rest.getFirst();
            members.delete(next);
            members.flush();
            members.save(new FamilyMember(familyId, next.getUserId(), FamilyMember.Role.OWNER));
        }
    }

    // --- The starting check -----------------------------------------------------------------------------------------

    /** The movements to ask about for the child's age, from both of Pat's charts. */
    @Transactional(readOnly = true)
    public HomeViews.Check check(Long childId) {
        Child child = homeChild(childId);
        int age = child.ageMonthsOn(today());
        Set<Long> secure = achievements.findByChildId(childId).stream().map(a -> a.getMilestone().getId())
                .collect(Collectors.toSet());
        List<HomeViews.Question> questions = milestones.findAllByOrderByChartAscPositionAsc().stream()
                .filter(m -> HomeRules.askAbout(m, age))
                .map(m -> new HomeViews.Question(m.getId(), m.getChart(), m.getChart().label(), m.getBand(),
                        m.getDescription(), secure.contains(m.getId())))
                .toList();
        return new HomeViews.Check(child.getId(), child.displayName(), age, HomeViews.NOT_A_DIAGNOSIS, questions);
    }

    /**
     * Keeps each "Yes" as a secure movement with the child's age, and suggests a colour. The first check places the
     * child at the suggested colour; after that the parent chooses whether to change.
     */
    @Transactional
    public HomeViews.CheckResult submitCheck(Long childId, HomeViews.CheckIn in) {
        Child child = homeChild(childId);
        CurrentUser me = currentUser.current();
        LocalDate today = today();
        int age = child.ageMonthsOn(today);
        Map<Long, Milestone> asked = milestones.findAllByOrderByChartAscPositionAsc().stream()
                .filter(m -> HomeRules.askAbout(m, age)).collect(Collectors.toMap(Milestone::getId, m -> m));
        Set<Long> secure = achievements.findByChildId(childId).stream().map(a -> a.getMilestone().getId())
                .collect(Collectors.toCollection(HashSet::new));
        int yes = 0;
        int notYet = 0;
        int notSure = 0;
        for (HomeViews.Answer a : in == null || in.answers() == null ? List.<HomeViews.Answer>of() : in.answers()) {
            Milestone m = a.milestoneId() == null ? null : asked.get(a.milestoneId());
            if (m == null || a.answer() == null) {
                continue;
            }
            switch (a.answer()) {
                case "YES" -> {
                    yes++;
                    if (secure.add(m.getId())) {
                        achievements.save(new MilestoneAchievement(UUID.randomUUID().toString(), child, m,
                                age, YearPoint.on(today), today, me.email()));
                    }
                }
                case "NOT_YET" -> notYet++;
                case "NOT_SURE" -> notSure++;
                default -> throw new IllegalArgumentException("Answers are YES, NOT_YET or NOT_SURE");
            }
        }
        HomeRules.Suggestion s = HomeRules.suggest(age, yes, notYet);
        if (child.getCurrentStage() == null) {
            child.placeAt(s.colour());
        }
        child.checkedOn(today);
        String explanation = s.earlier()
                ? "Starting at " + s.colour().label() + ", the colour before the one for " + age + " months, gives "
                        + "your child time to feel confident with the movements they are still learning."
                : s.forAge().label() + " is the colour for children of " + age + " months.";
        return new HomeViews.CheckResult(s.colour(), s.colour().displayName(), s.forAge(), s.forAge().displayName(),
                s.earlier(), yes, notYet, notSure, explanation);
    }

    /** The parent picks the colour to work on (the suggestion, or another). */
    @Transactional
    public HomeViews.HomeChild setStage(Long childId, ColourStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("Choose a colour");
        }
        Child child = homeChild(childId);
        child.placeAt(stage);
        return homeChild(child, today());
    }

    // --- The daily session ------------------------------------------------------------------------------------------

    /**
     * Today's session for a child: a home child, or a school child the parent is linked to (they can do the home
     * sessions too, but their teacher does the rating).
     */
    @Transactional(readOnly = true)
    public HomeViews.Today today(Long childId) {
        requireEnabled();
        Child child = findChild(childId);
        access.requireParentOf(child);
        LocalDate today = today();
        ColourStage stage = child.getCurrentStage() == null ? ColourStage.RED : child.getCurrentStage();
        List<Exercise> list = exercises.findByStageOrderByNumber(stage);
        long done = sessions.countByChildIdAndStage(childId, stage);
        LocalDate from = today.minusDays(COUNTER_DAYS - 1);
        Set<LocalDate> days = sessions.findByChildIdAndDoneOnBetween(childId, from, today).stream()
                .map(HomeProgrammeSession::getDoneOn).collect(Collectors.toSet());
        List<Boolean> grid = from.datesUntil(today.plusDays(1)).map(days::contains).toList();
        boolean canRate = child.isAtHome();
        List<HomeViews.Level> levels = new ArrayList<>();
        for (int i = 0; i < HomeViews.LEVELS.size(); i++) {
            levels.add(new HomeViews.Level(i + 1, HomeViews.LEVELS.get(i)));
        }
        boolean checkDue = checkDue(child, today);
        if (list.isEmpty()) {
            return new HomeViews.Today(child.getId(), child.displayName(), stage, stage.displayName(), false, 0, 0, 0,
                    HomeRules.SESSIONS_PER_MODULE, false, List.of(), List.of(), canRate, days.contains(today), grid,
                    days.size(), COUNTER_DAYS, sessions.countByChildId(childId), null, checkDue,
                    new HomeViews.Support(false, null), levels);
        }
        List<ExerciseScore> theirs = scores.findByChildIdAndExerciseStage(childId, stage);
        Set<String> rated = theirs.stream().map(s -> s.getExercise().getId() + ":" + s.getPoint())
                .collect(Collectors.toSet());
        HomeRules.Plan plan = HomeRules.plan(list, done);
        HomeRules.Review due = canRate ? HomeRules.reviewDue(list, plan, rated) : null;
        List<Long> firstTime = canRate ? plan.today().stream()
                .filter(e -> e.getPoints().contains(ExercisePoints.START)
                        && !rated.contains(e.getId() + ":" + ExercisePoints.START))
                .map(Exercise::getId).toList() : List.of();
        return new HomeViews.Today(child.getId(), child.displayName(), stage, stage.displayName(), true, plan.module(),
                plan.modules(), plan.sessionInModule(), HomeRules.SESSIONS_PER_MODULE, plan.colourComplete(),
                plan.today().stream().map(AssessmentViews.ExerciseView::of).toList(), firstTime, canRate,
                days.contains(today), grid, days.size(), COUNTER_DAYS, sessions.countByChildId(childId),
                due == null ? null : new HomeViews.Review(due.point(), ExercisePoints.label(due.point(), stage),
                        due.exercises().stream().map(AssessmentViews.ExerciseView::of).toList()),
                checkDue, canRate ? support(stage, theirs) : new HomeViews.Support(false, null), levels);
    }

    /** Accepts a batch of home sessions, typically saved on the phone while offline. Safe to resend. */
    @Transactional
    public HomeViews.SubmitResult recordSessions(List<HomeViews.SessionIn> batch) {
        requireEnabled();
        CurrentUser me = currentUser.current();
        LocalDate today = today();
        int saved = 0;
        int duplicates = 0;
        for (HomeViews.SessionIn s : batch) {
            if (s == null || s.clientId() == null || s.clientId().isBlank() || s.clientId().length() > 64
                    || s.childId() == null || s.doneOn() == null) {
                throw new IllegalArgumentException("Each session needs a clientId, childId and doneOn");
            }
            if (sessions.existsByClientId(s.clientId())) {
                duplicates++;
                continue;
            }
            if (s.doneOn().isAfter(today.plusDays(1)) || s.doneOn().isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
                throw new IllegalArgumentException("Sessions can be recorded for the last " + MAX_BACKDATE_DAYS
                        + " days only");
            }
            Child child = findChild(s.childId());
            access.requireParentOf(child);
            ColourStage stage = child.getCurrentStage() == null ? ColourStage.RED : child.getCurrentStage();
            sessions.save(new HomeProgrammeSession(s.clientId(), child.getId(), stage, s.doneOn(), me.email()));
            saved++;
        }
        return new HomeViews.SubmitResult(saved, duplicates);
    }

    // --- Progress ---------------------------------------------------------------------------------------------------

    /** A home child's progress, in the parent's words: movements secure, and each exercise's first and latest. */
    @Transactional(readOnly = true)
    public HomeViews.Progress progress(Long childId) {
        Child child = homeChild(childId);
        LocalDate today = today();
        List<MilestoneAchievement> mine = achievements.findByChildId(childId);
        Map<Chart, Long> totals = milestones.findAll().stream()
                .collect(Collectors.groupingBy(Milestone::getChart, Collectors.counting()));
        // New: secure since the first check (what the first check found was already there) and in the last months.
        LocalDate firstCheck = mine.stream().map(MilestoneAchievement::getAchievedOn).min(Comparator.naturalOrder())
                .orElse(today);
        LocalDate recent = today.minusDays(HomeRules.RECHECK_AFTER_DAYS);
        List<HomeViews.ChartProgress> charts = new ArrayList<>();
        for (Chart chart : Chart.values()) {
            List<MilestoneAchievement> on = mine.stream().filter(a -> a.getMilestone().getChart() == chart).toList();
            charts.add(new HomeViews.ChartProgress(chart, chart.label(), on.size(),
                    totals.getOrDefault(chart, 0L).intValue(),
                    on.stream()
                            .filter(a -> a.getAchievedOn().isAfter(firstCheck) && !a.getAchievedOn().isBefore(recent))
                            .sorted(Comparator.comparing((MilestoneAchievement a) -> a.getMilestone().getPosition()))
                            .map(a -> a.getMilestone().getDescription()).toList()));
        }
        ColourStage stage = child.getCurrentStage();
        List<HomeViews.ExerciseProgress> rows = List.of();
        HomeViews.Support support = new HomeViews.Support(false, null);
        if (stage != null) {
            List<ExerciseScore> theirs = scores.findByChildIdAndExerciseStage(childId, stage);
            Map<Long, List<ExerciseScore>> byExercise = theirs.stream()
                    .collect(Collectors.groupingBy(s -> s.getExercise().getId()));
            rows = exercises.findByStageOrderByNumber(stage).stream().filter(e -> byExercise.containsKey(e.getId()))
                    .map(e -> {
                        List<ExerciseScore> list = byExercise.get(e.getId()).stream()
                                .sorted(Comparator.comparing(ExerciseScore::getPoint, ExercisePoints.ORDER)).toList();
                        int first = list.getFirst().getScore();
                        int latest = list.getLast().getScore();
                        return new HomeViews.ExerciseProgress(e.getId(), e.getNumber(), e.getTitle(),
                                HomeViews.level(first), HomeViews.level(latest), latest > first);
                    }).toList();
            support = support(stage, theirs);
        }
        return new HomeViews.Progress(homeChild(child, today), HomeViews.NOT_A_DIAGNOSIS, charts, stage,
                stage == null ? null : stage.displayName(), rows, sessions.countByChildId(childId), support);
    }

    // --- Linking a school -------------------------------------------------------------------------------------------

    /**
     * The child's school has joined Motor Movers and given the parent a code. Links the parent to the school's record
     * and moves the home record onto it (the school's own records win where both have one), then removes the home
     * child. From then on the school sees what was recorded at home, as the parent agreed.
     */
    @Transactional
    public FamilyViews.LinkedChild joinSchool(Long childId, String code) {
        Child home = homeChild(childId);
        FamilyViews.LinkedChild linked = familyService.redeem(code);
        Child school = findChild(linked.childId());
        achievements.moveMissing(home, school);
        scores.moveMissing(home, school);
        sessions.moveSessions(home.getId(), school.getId());
        children.delete(home);
        return linked;
    }

    // --- Helpers ----------------------------------------------------------------------------------------------------

    private HomeViews.Support support(ColourStage stage, List<ExerciseScore> theirs) {
        boolean flagged = ExtraSupportRule.apply(stage, theirs.stream()
                .map(s -> new ExtraSupportRule.Mark(s.getExercise().getTitle(), s.getPoint(), s.getScore()))
                .toList()).flagged();
        return new HomeViews.Support(flagged, flagged ? HomeViews.SUPPORT_PROMPT : null);
    }

    private HomeViews.HomeChild homeChild(Child c, LocalDate today) {
        ColourStage stage = c.getCurrentStage();
        return new HomeViews.HomeChild(c.getId(), c.displayName(), c.ageMonthsOn(today), stage,
                stage == null ? null : stage.displayName(), c.getHomeCheckOn(), checkDue(c, today));
    }

    private static boolean checkDue(Child c, LocalDate today) {
        return c.isAtHome() && (c.getHomeCheckOn() == null
                || c.getHomeCheckOn().plusDays(HomeRules.RECHECK_AFTER_DAYS).isBefore(today));
    }

    /** A home child of the signed-in parent's family. */
    private Child homeChild(Long childId) {
        requireEnabled();
        Child child = findChild(childId);
        if (!child.isAtHome()) {
            throw new NotFoundException("Home child", childId);
        }
        access.requireFamilyOf(child);
        return child;
    }

    private Child findChild(Long id) {
        return children.findById(id).orElseThrow(() -> new NotFoundException("Child", id));
    }

    private CurrentUser requireParent() {
        requireEnabled();
        return currentUser.current();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new AccessDeniedException(NOT_AVAILABLE);
        }
    }

    private static String name(HomeViews.ChildIn in) {
        String name = in == null || in.name() == null ? "" : in.name().strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Give a first name or nickname of up to " + MAX_NAME + " characters");
        }
        return name;
    }

    private static int age(HomeViews.ChildIn in) {
        if (in == null || in.ageMonths() == null || in.ageMonths() < 0 || in.ageMonths() > MAX_AGE_MONTHS) {
            throw new IllegalArgumentException("Give your child's age in months (0 to " + MAX_AGE_MONTHS + ")");
        }
        return in.ageMonths();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
