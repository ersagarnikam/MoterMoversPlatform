package uk.co.motormovers.platform.assessment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.assessment.dto.AssessmentViews;
import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.assessment.model.ColourSummary;
import uk.co.motormovers.platform.assessment.model.DevelopmentComment;
import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;
import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.repository.ColourSummaryRepository;
import uk.co.motormovers.platform.assessment.repository.DevelopmentCommentRepository;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.ExerciseScoreRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneAchievementRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.assessment.util.ExtraSupportRule;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.model.ChildAccessLog;
import uk.co.motormovers.platform.child.repository.ChildAccessLogRepository;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.user.model.Role;

/**
 * Pat's two assessments: the developmental milestone charts (the age in months when a movement is secure, at the
 * start, middle or end of the school year) and the 1 to 4 exercise scores (at the start of a colour, the end of each
 * module and the end of the colour). See docs/assessment-design.md.
 */
@Service
public class AssessmentService {

    /** Records queued offline can be sent up to this many days later. */
    static final int MAX_BACKDATE_DAYS = 30;

    /** The charts run from birth to 7 years; a primary child is well under 10. */
    static final int MAX_AGE_MONTHS = 120;

    static final int MAX_NOTE = 2000;

    private final MilestoneRepository milestones;
    private final MilestoneAchievementRepository achievements;
    private final DevelopmentCommentRepository comments;
    private final ExerciseRepository exercises;
    private final ExerciseScoreRepository scores;
    private final ColourSummaryRepository summaries;
    private final ChildRepository children;
    private final SchoolClassRepository classes;
    private final SchoolRepository schools;
    private final ChildAccessLogRepository accessLog;
    private final AccessPolicy access;
    private final Clock clock;

    public AssessmentService(MilestoneRepository milestones, MilestoneAchievementRepository achievements,
            DevelopmentCommentRepository comments, ExerciseRepository exercises, ExerciseScoreRepository scores,
            ColourSummaryRepository summaries, ChildRepository children, SchoolClassRepository classes,
            SchoolRepository schools, ChildAccessLogRepository accessLog, AccessPolicy access, Clock clock) {
        this.milestones = milestones;
        this.achievements = achievements;
        this.comments = comments;
        this.exercises = exercises;
        this.scores = scores;
        this.summaries = summaries;
        this.children = children;
        this.classes = classes;
        this.schools = schools;
        this.accessLog = accessLog;
        this.access = access;
        this.clock = clock;
    }

    // --- Programme content -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AssessmentViews.MilestoneView> milestones() {
        return milestones.findAllByOrderByChartAscPositionAsc().stream().map(AssessmentViews.MilestoneView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssessmentViews.ExerciseView> exercises(ColourStage stage) {
        return exercises.findByStageOrderByNumber(stage).stream().map(AssessmentViews.ExerciseView::of).toList();
    }

    // --- Exercise scores -------------------------------------------------------------------------------------------

    /** The class sheet: the exercises of a colour (the class's own by default) and every child's scores on them. */
    @Transactional(readOnly = true)
    public AssessmentViews.ClassSheet classSheet(Long classId, ColourStage requested) {
        SchoolClass schoolClass = classes.findById(classId).orElseThrow(() -> new NotFoundException("Class", classId));
        access.requireClassAccess(schoolClass);
        ColourStage stage = requested == null ? schoolClass.getStage() : requested;
        List<Exercise> list = exercises.findByStageOrderByNumber(stage);
        List<Child> pupils = children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(classId);
        Map<Long, List<ExerciseScore>> byChild = pupils.isEmpty() ? Map.of()
                : scores.findByChildIdInAndExerciseStage(pupils.stream().map(Child::getId).toList(), stage).stream()
                        .collect(Collectors.groupingBy(s -> s.getChild().getId()));
        List<AssessmentViews.SheetChild> rows = pupils.stream().map(c -> {
            List<ExerciseScore> mine = byChild.getOrDefault(c.getId(), List.of());
            return new AssessmentViews.SheetChild(c.getId(), c.displayName(),
                    mine.stream().map(s -> new AssessmentViews.ScoreCell(s.getExercise().getId(), s.getPoint(),
                            s.getScore(), s.getAssessedOn())).toList(),
                    extraSupport(stage, mine).flagged());
        }).toList();
        return new AssessmentViews.ClassSheet(schoolClass.getId(), schoolClass.getName(), stage,
                stage.displayName(), list.stream().map(AssessmentViews.ExerciseView::of).toList(), rows);
    }

    /** Accepts a batch of scores, typically queued on a tablet while offline. Safe to resend. */
    @Transactional
    public AssessmentViews.SubmitResult submitScores(List<AssessmentViews.ExerciseScoreIn> batch) {
        int saved = 0;
        int duplicates = 0;
        Map<Long, Exercise> exerciseCache = new HashMap<>();
        for (AssessmentViews.ExerciseScoreIn s : batch) {
            if (s == null || !validClientId(s.clientId()) || s.childId() == null || s.exerciseId() == null
                    || s.point() == null || s.assessedOn() == null) {
                throw new IllegalArgumentException(
                        "Each score needs a clientId, childId, exerciseId, point and assessedOn");
            }
            if (scores.existsByClientId(s.clientId())) {
                duplicates++;
                continue;
            }
            checkDate(s.assessedOn());
            Child child = findChild(s.childId());
            CurrentUser me = requireRecorder(child);
            Exercise exercise = exerciseCache.computeIfAbsent(s.exerciseId(),
                    id -> exercises.findById(id).orElseThrow(() -> new NotFoundException("Exercise", id)));
            if (!exercise.getPoints().contains(s.point())) {
                throw new IllegalArgumentException(exercise.getTitle() + " is not assessed at "
                        + (ExercisePoints.valid(s.point()) ? ExercisePoints.label(s.point(), exercise.getStage())
                                : "that point"));
            }
            if (s.score() < Exercise.MIN_SCORE || s.score() > Exercise.MAX_SCORE) {
                throw new IllegalArgumentException("Score must be between " + Exercise.MIN_SCORE + " and "
                        + Exercise.MAX_SCORE);
            }
            Optional<ExerciseScore> existing = scores.findByChildIdAndExerciseIdAndPoint(child.getId(),
                    exercise.getId(), s.point());
            if (existing.isPresent()) {
                existing.get().rescore(s.clientId(), s.score(), s.assessedOn(), me.email());
            } else {
                scores.save(new ExerciseScore(s.clientId(), child, exercise, s.point(), s.score(), s.assessedOn(),
                        me.email()));
            }
            saved++;
        }
        return new AssessmentViews.SubmitResult(saved, duplicates);
    }

    // --- Developmental charts --------------------------------------------------------------------------------------

    /** A child's two charts with the movements now secure. Every view is logged. */
    @Transactional
    public AssessmentViews.Development development(Long childId) {
        Child child = findChild(childId);
        log(child, access.requireRecordAccess(child), "VIEW_DEVELOPMENT");
        LocalDate today = LocalDate.now(clock);
        Map<Long, MilestoneAchievement> mine = achievements.findByChildId(childId).stream()
                .collect(Collectors.toMap(a -> a.getMilestone().getId(), Function.identity()));
        List<DevelopmentComment> notes = comments.findByChildIdOrderBySchoolYearAscYearPointAsc(childId);
        Map<Chart, List<Milestone>> byChart = milestones.findAllByOrderByChartAscPositionAsc().stream()
                .collect(Collectors.groupingBy(Milestone::getChart, () -> new EnumMap<>(Chart.class),
                        Collectors.toList()));
        List<AssessmentViews.ChartView> charts = new ArrayList<>();
        byChart.forEach((chart, list) -> {
            List<AssessmentViews.ChartMilestone> rows = list.stream().map(m -> {
                MilestoneAchievement a = mine.get(m.getId());
                return new AssessmentViews.ChartMilestone(m.getId(), m.getPosition(), m.getBand(),
                        m.getAgeFromMonths(), m.getAgeToMonths(), m.getDescription(), a == null ? null
                                : new AssessmentViews.Achieved(a.getAgeMonths(), a.getYearPoint(),
                                        a.getYearPoint().label(), a.getSchoolYear(), a.getAchievedOn()));
            }).toList();
            charts.add(new AssessmentViews.ChartView(chart, chart.label(),
                    (int) rows.stream().filter(r -> r.achieved() != null).count(), rows.size(), rows,
                    notes.stream().filter(n -> n.getChart() == chart)
                            .map(n -> new AssessmentViews.CommentView(n.getYearPoint(), n.getYearPoint().label(),
                                    n.getSchoolYear(), n.getNote(), n.getWrittenOn()))
                            .toList()));
        });
        return new AssessmentViews.Development(child.getId(), child.displayName(), placeOf(child),
                AssessmentViews.NOT_A_DIAGNOSIS, YearPoint.schoolYear(today), YearPoint.on(today), charts);
    }

    /** Records movements as secure. A movement already secure stays as first recorded. Safe to resend. */
    @Transactional
    public AssessmentViews.SubmitResult submitAchievements(List<AssessmentViews.AchievementIn> batch) {
        int saved = 0;
        int duplicates = 0;
        for (AssessmentViews.AchievementIn a : batch) {
            if (a == null || !validClientId(a.clientId()) || a.childId() == null || a.milestoneId() == null
                    || a.yearPoint() == null || a.achievedOn() == null) {
                throw new IllegalArgumentException(
                        "Each record needs a clientId, childId, milestoneId, yearPoint and achievedOn");
            }
            if (achievements.existsByClientId(a.clientId())) {
                duplicates++;
                continue;
            }
            checkDate(a.achievedOn());
            if (a.ageMonths() < 0 || a.ageMonths() > MAX_AGE_MONTHS) {
                throw new IllegalArgumentException("Age in months must be between 0 and " + MAX_AGE_MONTHS);
            }
            Child child = findChild(a.childId());
            CurrentUser me = requireRecorder(child);
            Milestone milestone = milestones.findById(a.milestoneId())
                    .orElseThrow(() -> new NotFoundException("Movement", a.milestoneId()));
            if (achievements.findByChildIdAndMilestoneId(child.getId(), milestone.getId()).isPresent()) {
                duplicates++;
                continue;
            }
            achievements.save(new MilestoneAchievement(a.clientId(), child, milestone, a.ageMonths(), a.yearPoint(),
                    a.achievedOn(), me.email()));
            saved++;
        }
        return new AssessmentViews.SubmitResult(saved, duplicates);
    }

    /** Takes back a movement recorded as secure by mistake. */
    @Transactional
    public void clearAchievement(Long childId, Long milestoneId) {
        Child child = findChild(childId);
        requireRecorder(child);
        achievements.findByChildIdAndMilestoneId(childId, milestoneId).ifPresent(achievements::delete);
    }

    /** The comment box under a chart for this school year; an empty note removes it. */
    @Transactional
    public void writeComment(Long childId, AssessmentViews.CommentIn in) {
        if (in == null || in.chart() == null || in.yearPoint() == null) {
            throw new IllegalArgumentException("A comment needs a chart and a point in the year");
        }
        Child child = findChild(childId);
        CurrentUser me = requireRecorder(child);
        LocalDate today = LocalDate.now(clock);
        int year = YearPoint.schoolYear(today);
        String note = trimmed(in.note());
        Optional<DevelopmentComment> existing = comments.findByChildIdAndChartAndSchoolYearAndYearPoint(childId,
                in.chart(), year, in.yearPoint());
        if (note == null) {
            existing.ifPresent(comments::delete);
            return;
        }
        DevelopmentComment comment = existing
                .orElseGet(() -> new DevelopmentComment(childId, in.chart(), year, in.yearPoint()));
        comment.write(note, today, me.email());
        comments.save(comment);
    }

    // --- The profile -----------------------------------------------------------------------------------------------

    /**
     * Both assessments for one child (Pat: "how they are progressing with the movements and how they are progressing
     * overall against the developmental milestone"). The exercises are those of {@code requested}, or of the colour
     * the child is working at. Every view is logged.
     */
    @Transactional
    public AssessmentViews.Profile profile(Long childId, ColourStage requested) {
        Child child = findChild(childId);
        log(child, access.requireRecordAccess(child), "VIEW_ASSESSMENT");
        int year = YearPoint.schoolYear(LocalDate.now(clock));

        List<MilestoneAchievement> mine = achievements.findByChildId(childId);
        Map<Chart, Long> totals = milestones.findAll().stream()
                .collect(Collectors.groupingBy(Milestone::getChart, Collectors.counting()));
        List<AssessmentViews.ChartSummary> development = new ArrayList<>();
        for (Chart chart : Chart.values()) {
            List<MilestoneAchievement> onChart = mine.stream().filter(a -> a.getMilestone().getChart() == chart)
                    .toList();
            Map<YearPoint, Integer> thisYear = new EnumMap<>(YearPoint.class);
            for (YearPoint p : YearPoint.values()) {
                thisYear.put(p, (int) onChart.stream()
                        .filter(a -> a.getSchoolYear() == year && a.getYearPoint() == p).count());
            }
            development.add(new AssessmentViews.ChartSummary(chart, chart.label(), onChart.size(),
                    totals.getOrDefault(chart, 0L).intValue(), thisYear));
        }

        List<ColourStage> withExercises = AssessmentContent.STAGES_WITH_EXERCISES.stream()
                .filter(exercises::existsByStage).sorted().toList();
        ColourStage stage = requested != null ? requested : defaultStage(child, withExercises);
        List<AssessmentViews.ExerciseRow> rows = List.of();
        AssessmentViews.Totals totalsRow = null;
        ExtraSupportRule.Result support = new ExtraSupportRule.Result(false, List.of());
        AssessmentViews.Summary summary = null;
        if (stage != null) {
            List<Exercise> list = exercises.findByStageOrderByNumber(stage);
            List<ExerciseScore> theirs = scores.findByChildIdAndExerciseStage(childId, stage);
            Map<String, ExerciseScore> byKey = theirs.stream()
                    .collect(Collectors.toMap(s -> key(s.getExercise().getId(), s.getPoint()), Function.identity()));
            rows = list.stream().map(e -> new AssessmentViews.ExerciseRow(e.getId(), e.getNumber(), e.getTitle(),
                    e.getPoints().stream().map(p -> {
                        ExerciseScore s = byKey.get(key(e.getId(), p));
                        return new AssessmentViews.PointScore(p, ExercisePoints.label(p, stage),
                                s == null ? null : s.getScore(), s == null ? null : s.getAssessedOn());
                    }).toList())).toList();
            List<ExerciseScore> starts = theirs.stream().filter(s -> ExercisePoints.START.equals(s.getPoint()))
                    .toList();
            List<ExerciseScore> ends = theirs.stream().filter(s -> ExercisePoints.END.equals(s.getPoint())).toList();
            totalsRow = new AssessmentViews.Totals(starts.stream().mapToInt(ExerciseScore::getScore).sum(),
                    starts.size(), ends.stream().mapToInt(ExerciseScore::getScore).sum(), ends.size(),
                    list.size() * Exercise.MAX_SCORE);
            support = extraSupport(stage, theirs);
            summary = summaries.findByChildIdAndStage(childId, stage)
                    .map(s -> new AssessmentViews.Summary(s.getPersonalComment(), s.getAcademicComment(),
                            s.getWrittenOn()))
                    .orElse(null);
        }
        return new AssessmentViews.Profile(child.getId(), "Child " + child.getId(), child.displayName(),
                placeOf(child), AssessmentViews.NOT_A_DIAGNOSIS, child.getCurrentStage(), stage,
                stage == null ? null : stage.displayName(), withExercises, development, rows, totalsRow,
                new AssessmentViews.ExtraSupport(support.flagged(), AssessmentViews.EXTRA_SUPPORT, support.reasons()),
                summary);
    }

    /** Pat's summary sheet comments for a colour; both empty removes them. */
    @Transactional
    public void writeSummary(Long childId, AssessmentViews.SummaryIn in) {
        if (in == null || in.stage() == null) {
            throw new IllegalArgumentException("A summary needs a colour");
        }
        Child child = findChild(childId);
        CurrentUser me = requireRecorder(child);
        String personal = trimmed(in.personalComment());
        String academic = trimmed(in.academicComment());
        Optional<ColourSummary> existing = summaries.findByChildIdAndStage(childId, in.stage());
        if (personal == null && academic == null) {
            existing.ifPresent(summaries::delete);
            return;
        }
        ColourSummary summary = existing.orElseGet(() -> new ColourSummary(childId, in.stage()));
        summary.write(personal, academic, LocalDate.now(clock), me.email());
        summaries.save(summary);
    }

    // --- The head's overview ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AssessmentViews.SchoolOverview schoolOverview(Long schoolId) {
        School school = schools.findById(schoolId).orElseThrow(() -> new NotFoundException("School", schoolId));
        access.requireSchoolAccess(school);
        int year = YearPoint.schoolYear(LocalDate.now(clock));
        List<AssessmentViews.ClassOverview> rows = new ArrayList<>();
        List<AssessmentViews.FlaggedChild> flagged = new ArrayList<>();
        for (SchoolClass c : classes.findBySchoolIdOrderByName(schoolId)) {
            ClassFigures f = classFigures(c, year);
            rows.add(f.row());
            f.flagged().forEach((child, reasons) -> flagged.add(
                    new AssessmentViews.FlaggedChild(child.getId(), child.displayName(), c.getName(), reasons)));
        }
        return new AssessmentViews.SchoolOverview(school.getId(), school.getName(), year, rows, flagged);
    }

    /** For a trust or group lead's summary: no children, only how many. Callers check access. */
    @Transactional(readOnly = true)
    public SchoolFigures schoolFigures(Long schoolId) {
        int year = YearPoint.schoolYear(LocalDate.now(clock));
        int pupils = 0;
        int assessed = 0;
        int support = 0;
        for (SchoolClass c : classes.findBySchoolIdOrderByName(schoolId)) {
            ClassFigures f = classFigures(c, year);
            pupils += f.row().children();
            assessed += f.developmentallyAssessed();
            support += f.row().extraSupport();
        }
        return new SchoolFigures(pupils, assessed, support);
    }

    /** Children on roll, children with a developmental assessment this school year, children flagged. */
    public record SchoolFigures(int children, int developmentallyAssessed, int extraSupport) {
    }

    private record ClassFigures(AssessmentViews.ClassOverview row, int developmentallyAssessed,
            Map<Child, List<String>> flagged) {
    }

    private ClassFigures classFigures(SchoolClass c, int year) {
        List<Child> pupils = children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(c.getId());
        List<Long> ids = pupils.stream().map(Child::getId).toList();
        List<MilestoneAchievement> secured = ids.isEmpty() ? List.of()
                : achievements.findByChildIdInAndSchoolYear(ids, year);
        Map<YearPoint, Integer> developmental = new EnumMap<>(YearPoint.class);
        for (YearPoint p : YearPoint.values()) {
            developmental.put(p, (int) secured.stream().filter(a -> a.getYearPoint() == p)
                    .map(a -> a.getChild().getId()).distinct().count());
        }
        int assessed = (int) secured.stream().map(a -> a.getChild().getId()).distinct().count();

        Map<Long, List<ExerciseScore>> byChild = ids.isEmpty() ? Map.of()
                : scores.findByChildIdInAndExerciseStage(ids, c.getStage()).stream()
                        .collect(Collectors.groupingBy(s -> s.getChild().getId()));
        Map<Integer, Integer> levels = new TreeMap<>();
        for (int s = Exercise.MIN_SCORE; s <= Exercise.MAX_SCORE; s++) {
            levels.put(s, 0);
        }
        Map<Child, List<String>> flagged = new LinkedHashMap<>();
        for (Child child : pupils) {
            List<ExerciseScore> mine = byChild.getOrDefault(child.getId(), List.of());
            // The latest score on each exercise (the one at the furthest point reached); the child counts once, at
            // the middle of those scores.
            List<Integer> latest = mine.stream().collect(Collectors.groupingBy(s -> s.getExercise().getId(),
                    Collectors.maxBy(Comparator.comparing(ExerciseScore::getPoint, ExercisePoints.ORDER))))
                    .values().stream().flatMap(Optional::stream).map(ExerciseScore::getScore).sorted().toList();
            if (!latest.isEmpty()) {
                levels.merge(latest.get((latest.size() - 1) / 2), 1, Integer::sum);
            }
            ExtraSupportRule.Result r = extraSupport(c.getStage(), mine);
            if (r.flagged()) {
                flagged.put(child, r.reasons());
            }
        }
        double perChild = assessed == 0 ? 0 : Math.round(10.0 * secured.size() / assessed) / 10.0;
        return new ClassFigures(new AssessmentViews.ClassOverview(c.getId(), c.getName(), c.getStage(),
                c.getStage().displayName(), pupils.size(), developmental, perChild, levels, flagged.size()),
                assessed, flagged);
    }

    // --- Helpers ---------------------------------------------------------------------------------------------------

    private static ExtraSupportRule.Result extraSupport(ColourStage stage, List<ExerciseScore> list) {
        return ExtraSupportRule.apply(stage, list.stream()
                .map(s -> new ExtraSupportRule.Mark(s.getExercise().getTitle(), s.getPoint(), s.getScore()))
                .toList());
    }

    private static ColourStage defaultStage(Child child, List<ColourStage> withExercises) {
        for (ColourStage s : Stream.of(child.getCurrentStage(),
                child.getSchoolClass() == null ? null : child.getSchoolClass().getStage())
                .filter(Objects::nonNull).toList()) {
            if (withExercises.contains(s)) {
                return s;
            }
        }
        return withExercises.isEmpty() ? null : withExercises.getFirst();
    }

    /** The class name, or "At home" for a family's own child. */
    private static String placeOf(Child child) {
        return child.getSchoolClass() == null ? Child.HOME_YEAR_GROUP : child.getSchoolClass().getName();
    }

    private Child findChild(Long id) {
        return children.findById(id).orElseThrow(() -> new NotFoundException("Child", id));
    }

    /**
     * Teachers (their own class), heads and the team record assessments; trust or group leads only look. For a home
     * child (Motor Movers at Home), the child's family.
     */
    private CurrentUser requireRecorder(Child child) {
        CurrentUser me = access.requireRecordAccess(child);
        if (me.is(Role.TRUST_LEAD)) {
            throw new AccessDeniedException("Trust or group leads cannot record assessments");
        }
        if (child.getLeftOn() != null) {
            throw new IllegalArgumentException("This child has left the school");
        }
        return me;
    }

    /** The school's record access log. A home child's record is the family's own, so it has none. */
    private void log(Child child, CurrentUser me, String action) {
        if (child.isAtHome()) {
            return;
        }
        accessLog.save(new ChildAccessLog(child.getId(), child.getSchoolClass().getSchool().getId(), me.email(),
                action));
    }

    private void checkDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        // One day of slack for time zones.
        if (date.isAfter(today.plusDays(1)) || date.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            throw new IllegalArgumentException("Assessments can be recorded for the last " + MAX_BACKDATE_DAYS
                    + " days only");
        }
    }

    private static boolean validClientId(String clientId) {
        return clientId != null && !clientId.isBlank() && clientId.length() <= 64;
    }

    private static String trimmed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.strip();
        if (t.length() > MAX_NOTE) {
            throw new IllegalArgumentException("Comments can be up to " + MAX_NOTE + " characters");
        }
        return t;
    }

    private static String key(Long exerciseId, String point) {
        return exerciseId + ":" + point;
    }
}
