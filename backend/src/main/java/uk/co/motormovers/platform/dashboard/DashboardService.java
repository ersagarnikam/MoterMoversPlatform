package uk.co.motormovers.platform.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.Child;
import uk.co.motormovers.platform.child.ChildAccessLogRepository;
import uk.co.motormovers.platform.child.ChildRepository;
import uk.co.motormovers.platform.classroom.DailySessionRecord;
import uk.co.motormovers.platform.classroom.DailySessionRecordRepository;
import uk.co.motormovers.platform.common.ColourStage;
import uk.co.motormovers.platform.common.NotFoundException;
import uk.co.motormovers.platform.common.PlatformProperties;
import uk.co.motormovers.platform.common.SchoolDays;
import uk.co.motormovers.platform.school.Licence;
import uk.co.motormovers.platform.school.LicenceRepository;
import uk.co.motormovers.platform.school.School;
import uk.co.motormovers.platform.school.SchoolClass;
import uk.co.motormovers.platform.school.SchoolClassRepository;
import uk.co.motormovers.platform.school.SchoolRepository;
import uk.co.motormovers.platform.school.Trust;
import uk.co.motormovers.platform.school.TrustRepository;
import uk.co.motormovers.platform.screening.ScreeningResultRepository;
import uk.co.motormovers.platform.security.AccessPolicy;

@Service
public class DashboardService {

    /** Participation is measured over the last 20 school days. */
    public static final int WINDOW_DAYS = 20;

    /** A class with no session in this many school days counts as inactive. */
    static final int ACTIVE_WITHIN_DAYS = 10;

    private final SchoolRepository schools;
    private final TrustRepository trusts;
    private final SchoolClassRepository classes;
    private final ChildRepository children;
    private final ScreeningResultRepository screening;
    private final DailySessionRecordRepository records;
    private final LicenceRepository licences;
    private final ChildAccessLogRepository accessLog;
    private final AccessPolicy access;
    private final PlatformProperties properties;
    private final Clock clock;

    public DashboardService(SchoolRepository schools, TrustRepository trusts, SchoolClassRepository classes,
            ChildRepository children, ScreeningResultRepository screening, DailySessionRecordRepository records,
            LicenceRepository licences, ChildAccessLogRepository accessLog, AccessPolicy access,
            PlatformProperties properties, Clock clock) {
        this.schools = schools;
        this.trusts = trusts;
        this.classes = classes;
        this.children = children;
        this.screening = screening;
        this.records = records;
        this.licences = licences;
        this.accessLog = accessLog;
        this.access = access;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardViews.SchoolDashboard school(Long schoolId) {
        School school = findSchool(schoolId);
        access.requireSchoolAccess(school);
        return buildSchool(school);
    }

    @Transactional(readOnly = true)
    public DashboardViews.TrustDashboard trust(Long trustId) {
        access.requireTrustAccess(trustId);
        Trust trust = trusts.findById(trustId).orElseThrow(() -> new NotFoundException("Trust", trustId));
        LocalDate today = LocalDate.now(clock);
        List<DashboardViews.TrustSchoolRow> rows = schools.findByTrustIdOrderByName(trustId).stream().map(s -> {
            DashboardViews.SchoolDashboard d = buildSchool(s);
            Licence licence = licences.findFirstBySchoolIdOrderByEndsOnDesc(s.getId()).orElse(null);
            return new DashboardViews.TrustSchoolRow(s.getId(), s.getName(),
                    licence == null ? Licence.Status.EXPIRED : licence.statusOn(today),
                    licence == null ? null : licence.getPlan().label(),
                    licence == null ? null : licence.getEndsOn(),
                    licence == null ? "" : describeStages(licence.getStages()),
                    d.childrenTotal() == 0 ? 0 : (double) d.childrenScreened() / d.childrenTotal(),
                    d.dailyParticipation());
        }).toList();
        return new DashboardViews.TrustDashboard(trust.getId(), trust.getName(), rows);
    }

    @Transactional(readOnly = true)
    public List<DashboardViews.AccessLogEntry> accessLog(Long schoolId) {
        access.requireSchoolAccess(findSchool(schoolId));
        return accessLog.findTop200BySchoolIdOrderByAtDesc(schoolId).stream()
                .map(e -> new DashboardViews.AccessLogEntry(e.getChildId(), e.getUserEmail(), e.getAction(),
                        e.getAt().toString()))
                .toList();
    }

    /** Governor summary, one row per class. */
    @Transactional(readOnly = true)
    public String governorSummaryCsv(Long schoolId) {
        DashboardViews.SchoolDashboard d = school(schoolId);
        StringBuilder csv = new StringBuilder("class,stage,participation_last_20_days,flagged\n");
        d.classes().forEach(c -> csv.append(csvField(c.name())).append(',').append(csvField(c.stageName()))
                .append(',').append(percent(c.participation())).append(',').append(c.flagged()).append('\n'));
        csv.append("\nchildren_screened,").append(d.childrenScreened()).append('/').append(d.childrenTotal())
                .append("\nmoved_up_a_stage,").append(percent(d.movedUpRate()))
                .append("\ndaily_participation,").append(percent(d.dailyParticipation()))
                .append("\nclasses_active,").append(d.classesActive()).append('/').append(d.classesTotal())
                .append('\n');
        return csv.toString();
    }

    /** Anonymised research file: no names, initials or record ids; a per-export sequence number only. */
    @Transactional(readOnly = true)
    public String researchCsv(Long schoolId) {
        School school = findSchool(schoolId);
        access.requireSchoolAccess(school);
        List<Child> pupils = children.findBySchoolClassSchoolIdAndLeftOnIsNull(schoolId);
        StringBuilder csv = new StringBuilder("subject,year_group,starting_stage,current_stage,moved_up\n");
        int n = 1;
        for (Child c : pupils) {
            csv.append(n++).append(',').append(csvField(c.getYearGroup())).append(',')
                    .append(c.getStartingStage() == null ? "" : c.getStartingStage().label()).append(',')
                    .append(c.getCurrentStage() == null ? "" : c.getCurrentStage().label()).append(',')
                    .append(c.hasMovedUp()).append('\n');
        }
        return csv.toString();
    }

    private DashboardViews.SchoolDashboard buildSchool(School school) {
        LocalDate today = LocalDate.now(clock);
        List<SchoolClass> schoolClasses = classes.findBySchoolIdOrderByName(school.getId());
        List<Child> pupils = children.findBySchoolClassSchoolIdAndLeftOnIsNull(school.getId());
        Set<Long> screened = pupils.isEmpty() ? Set.of()
                : Set.copyOf(screening.findScreenedChildIds(pupils.stream().map(Child::getId).toList()));
        long movedUp = pupils.stream().filter(Child::hasMovedUp).count();
        long placed = pupils.stream().filter(c -> c.getStartingStage() != null).count();

        List<LocalDate> window = SchoolDays.lastSchoolDays(today, WINDOW_DAYS);
        LocalDate activeSince = SchoolDays.lastSchoolDays(today, ACTIVE_WITHIN_DAYS).getFirst();
        Map<Long, List<DailySessionRecord>> byClass = schoolClasses.isEmpty() ? Map.of()
                : records.findBySchoolClassIdInAndSessionDateBetween(
                        schoolClasses.stream().map(SchoolClass::getId).toList(), window.getFirst(), window.getLast())
                        .stream().collect(Collectors.groupingBy(r -> r.getSchoolClass().getId()));

        double threshold = properties.participationFlagThreshold();
        List<DashboardViews.ClassParticipation> rows = schoolClasses.stream().map(c -> {
            double rate = (double) byClass.getOrDefault(c.getId(), List.of()).size() / WINDOW_DAYS;
            return new DashboardViews.ClassParticipation(c.getId(), c.getName(), c.getStage().displayName(), rate,
                    rate < threshold);
        }).toList();
        int active = (int) schoolClasses.stream()
                .filter(c -> byClass.getOrDefault(c.getId(), List.of()).stream()
                        .anyMatch(r -> !r.getSessionDate().isBefore(activeSince)))
                .count();
        double participation = rows.stream().mapToDouble(DashboardViews.ClassParticipation::participation)
                .average().orElse(0);

        return new DashboardViews.SchoolDashboard(school.getId(), school.getName(), screened.size(), pupils.size(),
                placed == 0 ? 0 : (double) movedUp / placed, participation, active, schoolClasses.size(),
                WINDOW_DAYS, threshold, rows);
    }

    private School findSchool(Long id) {
        return schools.findById(id).orElseThrow(() -> new NotFoundException("School", id));
    }

    /** "Red to Blue" for a contiguous run, otherwise a list ("Red, Orange, Green"). */
    static String describeStages(Set<ColourStage> stages) {
        if (stages.isEmpty()) {
            return "";
        }
        List<ColourStage> sorted = stages.stream().sorted(Comparator.naturalOrder()).toList();
        boolean contiguous = sorted.getLast().ordinal() - sorted.getFirst().ordinal() == sorted.size() - 1;
        if (contiguous && sorted.size() > 2) {
            return sorted.getFirst().label() + " to " + sorted.getLast().label();
        }
        return sorted.stream().map(ColourStage::label).collect(Collectors.joining(", "));
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    /** Quotes where needed and neutralises spreadsheet formulas. */
    static String csvField(String value) {
        if (value == null) {
            return "";
        }
        String v = value.matches("^[=+\\-@].*") ? "'" + value : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
