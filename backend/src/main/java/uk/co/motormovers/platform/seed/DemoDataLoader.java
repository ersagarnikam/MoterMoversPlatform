package uk.co.motormovers.platform.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.Child;
import uk.co.motormovers.platform.child.ChildRepository;
import uk.co.motormovers.platform.classroom.DailySessionRecord;
import uk.co.motormovers.platform.classroom.DailySessionRecordRepository;
import uk.co.motormovers.platform.common.ColourStage;
import uk.co.motormovers.platform.common.PlatformProperties;
import uk.co.motormovers.platform.common.SchoolDays;
import uk.co.motormovers.platform.content.ContentStatus;
import uk.co.motormovers.platform.content.ProgrammeSession;
import uk.co.motormovers.platform.content.ProgrammeSessionRepository;
import uk.co.motormovers.platform.school.Licence;
import uk.co.motormovers.platform.school.LicencePlan;
import uk.co.motormovers.platform.school.LicenceRepository;
import uk.co.motormovers.platform.school.School;
import uk.co.motormovers.platform.school.SchoolClass;
import uk.co.motormovers.platform.school.SchoolClassRepository;
import uk.co.motormovers.platform.school.SchoolRepository;
import uk.co.motormovers.platform.school.Trust;
import uk.co.motormovers.platform.school.TrustRepository;
import uk.co.motormovers.platform.screening.MotorArea;
import uk.co.motormovers.platform.screening.ScreeningItem;
import uk.co.motormovers.platform.screening.ScreeningItemRepository;
import uk.co.motormovers.platform.screening.ScreeningResult;
import uk.co.motormovers.platform.screening.ScreeningResultRepository;
import uk.co.motormovers.platform.training.Cohort;
import uk.co.motormovers.platform.training.CohortRepository;
import uk.co.motormovers.platform.training.Course;
import uk.co.motormovers.platform.training.CourseRepository;
import uk.co.motormovers.platform.training.CourseTrack;
import uk.co.motormovers.platform.user.AppUser;
import uk.co.motormovers.platform.user.AppUserRepository;
import uk.co.motormovers.platform.user.Role;

/**
 * Example data for local development and demos. Names, schools and figures are examples only,
 * matching the sketches in the platform overview. Every demo account uses the password "password".
 */
@Component
@ConditionalOnProperty(name = "platform.seed-demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    public static final String DEMO_PASSWORD = "password";

    private final TrustRepository trusts;
    private final SchoolRepository schools;
    private final LicenceRepository licences;
    private final SchoolClassRepository classes;
    private final AppUserRepository users;
    private final ChildRepository children;
    private final ProgrammeSessionRepository content;
    private final DailySessionRecordRepository records;
    private final ScreeningItemRepository items;
    private final ScreeningResultRepository results;
    private final CourseRepository courses;
    private final CohortRepository cohorts;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties properties;
    private final Clock clock;
    private final Random random = new Random(42);

    public DemoDataLoader(TrustRepository trusts, SchoolRepository schools, LicenceRepository licences,
            SchoolClassRepository classes, AppUserRepository users, ChildRepository children,
            ProgrammeSessionRepository content, DailySessionRecordRepository records, ScreeningItemRepository items,
            ScreeningResultRepository results, CourseRepository courses, CohortRepository cohorts,
            PasswordEncoder passwordEncoder, PlatformProperties properties, Clock clock) {
        this.trusts = trusts;
        this.schools = schools;
        this.licences = licences;
        this.classes = classes;
        this.users = users;
        this.children = children;
        this.content = content;
        this.records = records;
        this.items = items;
        this.results = results;
        this.courses = courses;
        this.cohorts = cohorts;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        seedScreeningItems();
        Map<ColourStage, List<ProgrammeSession>> sessions = seedContent();

        Trust trust = trusts.save(new Trust("Northbridge Academy Trust"));
        School oakfield = school("Oakfield Primary", trust, LicencePlan.TRUST,
                EnumSet.range(ColourStage.RED, ColourStage.BLUE), today.minusMonths(3), today.plusMonths(9));
        School marlow = school("Marlow Infants", trust, LicencePlan.TRUST,
                EnumSet.range(ColourStage.RED, ColourStage.YELLOW), today.minusMonths(5), today.plusMonths(7));
        School brookhouse = school("Brookhouse", trust, LicencePlan.TRUST,
                EnumSet.range(ColourStage.RED, ColourStage.GREEN), today.minusMonths(11), today.plusDays(30));
        School ferrers = school("Ferrers Park", trust, LicencePlan.FREE_TRIAL,
                EnumSet.of(ColourStage.RED, ColourStage.ORANGE), today.minusMonths(1), today.plusMonths(2));

        users.save(new AppUser("admin@motormovers.test", "Laura Preedy-Maher", hash, Role.ADMIN, null, null));
        users.save(new AppUser("head@oakfield.test", "Mr Patel", hash, Role.HEAD, oakfield.getId(), trust.getId()));
        users.save(new AppUser("trust@northbridge.test", "Ms Okafor", hash, Role.TRUST_LEAD, null, trust.getId()));
        users.save(new AppUser("practitioner@example.test", "Sam Rivers", hash, Role.PRACTITIONER, null, null));
        users.save(new AppUser("parent@example.test", "Jo Bennett", hash, Role.PARENT, null, null));
        AppUser hardy = users.save(new AppUser("teacher@oakfield.test", "Ms Hardy", hash, Role.TEACHER,
                oakfield.getId(), trust.getId()));

        List<SchoolClass> screenedClasses = new ArrayList<>();
        screenedClasses.add(classWithHistory(oakfield, "Reception A", "Reception", null, ColourStage.RED, 26, 18,
                sessions, today));
        screenedClasses.add(classWithHistory(oakfield, "Year 1 M", "Year 1", null, ColourStage.ORANGE, 27, 16,
                sessions, today));
        SchoolClass y2r = classWithHistory(oakfield, "Year 2 R", "Year 2", hardy, ColourStage.YELLOW, 28, 17,
                sessions, today);
        screenedClasses.add(y2r);
        screenedClasses.add(classWithHistory(oakfield, "Year 2 T", "Year 2", null, ColourStage.YELLOW, 27, 12,
                sessions, today));
        classWithHistory(oakfield, "Year 3 K", "Year 3", null, ColourStage.GREEN, 29, 18, sessions, today);
        screenedClasses.add(classWithHistory(marlow, "Reception B", "Reception", null, ColourStage.RED, 25, 19,
                sessions, today));
        classWithHistory(marlow, "Year 1 P", "Year 1", null, ColourStage.ORANGE, 24, 17, sessions, today);
        classWithHistory(brookhouse, "Year 1 S", "Year 1", null, ColourStage.ORANGE, 26, 14, sessions, today);
        classWithHistory(brookhouse, "Year 2 L", "Year 2", null, ColourStage.YELLOW, 25, 12, sessions, today);
        classWithHistory(ferrers, "Reception F", "Reception", null, ColourStage.RED, 24, 12, sessions, today);

        screenedClasses.forEach(c -> seedScreeningHistory(c, today));
        seedTraining(today);
        log.info("Demo data loaded. Sign in with e.g. teacher@oakfield.test / {}", DEMO_PASSWORD);
    }

    private School school(String name, Trust trust, LicencePlan plan, EnumSet<ColourStage> stages, LocalDate from,
            LocalDate to) {
        School school = schools.save(new School(name, trust, properties.region()));
        licences.save(new Licence(school, plan, from, to, stages));
        return school;
    }

    private SchoolClass classWithHistory(School school, String name, String yearGroup, AppUser teacher,
            ColourStage stage, int size, int sessionsInLast20, Map<ColourStage, List<ProgrammeSession>> sessions,
            LocalDate today) {
        SchoolClass schoolClass = classes.save(new SchoolClass(school, name, yearGroup, teacher, stage));
        List<Child> pupils = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Child child = new Child(schoolClass, initials(), yearGroup);
            child.placeAt(stage);
            if (random.nextDouble() < 0.6 && stage != ColourStage.VIOLET) {
                child.placeAt(ColourStage.values()[stage.ordinal() + 1]);
            }
            pupils.add(child);
        }
        children.saveAll(pupils);

        // Leave today undone so the teacher can complete it in the demo.
        List<LocalDate> window = SchoolDays.lastSchoolDays(today.minusDays(1), 20);
        List<ProgrammeSession> sequence = sessions.get(stage);
        List<LocalDate> done = new ArrayList<>(window);
        while (done.size() > sessionsInLast20) {
            done.remove(random.nextInt(done.size()));
        }
        int n = 0;
        for (LocalDate date : done) {
            records.save(new DailySessionRecord(schoolClass, sequence.get(n++ % sequence.size()), date,
                    size - random.nextInt(3), random.nextInt(3), "demo"));
        }
        return schoolClass;
    }

    private String initials() {
        String letters = "ABCDEFGHJKLMNOPRSTW";
        return letters.charAt(random.nextInt(letters.length())) + "." + letters.charAt(random.nextInt(letters.length()))
                + ".";
    }

    private void seedScreeningItems() {
        Object[][] defs = {
            {"BAL_SINGLE_LEG", MotorArea.BALANCE, "Single-leg balance, eyes open",
                "Watch for up to 10 seconds on each leg. Record the better attempt. Do not prompt.",
                new String[] {"Cannot hold for 2 seconds", "2 to 4 seconds, marked sway", "5 to 9 seconds, some sway", "10 seconds, steady"}},
            {"BAL_HEEL_TOE", MotorArea.BALANCE, "Heel-to-toe walk along a line",
                "Ten steps along a floor line, arms by the sides.",
                new String[] {"Steps off the line within 3 steps", "4 to 6 steps on the line", "7 to 9 steps on the line", "10 steps, steady"}},
            {"GM_HOP", MotorArea.GROSS_MOTOR, "Hopping on one foot",
                "Up to 5 hops on the preferred foot, staying in a hoop.",
                new String[] {"Cannot hop", "1 to 2 hops", "3 to 4 hops", "5 hops, controlled"}},
            {"GM_JUMP", MotorArea.GROSS_MOTOR, "Two-footed jump forward",
                "Jump from a standing start, landing on both feet together.",
                new String[] {"Does not leave the floor with both feet", "Jumps, unbalanced landing", "Jumps, lands with a step", "Jumps and lands together, steady"}},
            {"MD_THREAD", MotorArea.MANUAL_DEXTERITY, "Threading beads",
                "Thread as many large beads as possible in 30 seconds.",
                new String[] {"0 to 1 beads", "2 to 3 beads", "4 to 5 beads", "6 or more beads"}},
            {"MD_PEGS", MotorArea.MANUAL_DEXTERITY, "Posting pegs",
                "Post 6 pegs into a board with the preferred hand.",
                new String[] {"Fewer than 3 pegs", "3 to 4 pegs", "5 pegs", "All 6 pegs, smoothly"}},
            {"AC_CATCH", MotorArea.AIMING_AND_CATCHING, "Two-handed catch",
                "Five underarm throws of a beanbag from 2 metres.",
                new String[] {"No catches", "1 to 2 catches", "3 to 4 catches", "5 catches"}},
            {"AC_THROW", MotorArea.AIMING_AND_CATCHING, "Throwing at a target",
                "Five beanbag throws at a hoop on the floor 2 metres away.",
                new String[] {"None in the hoop", "1 to 2 in the hoop", "3 to 4 in the hoop", "5 in the hoop"}},
            {"PC_SIT", MotorArea.POSTURAL_CONTROL, "Sitting posture at a table",
                "Observe for one minute while the child draws.",
                new String[] {"Slumps or leans on the table throughout", "Frequently slumps", "Occasionally slumps", "Upright throughout"}},
            {"PC_SUPERMAN", MotorArea.POSTURAL_CONTROL, "Lying on the tummy, arms and legs raised",
                "Hold the position for up to 10 seconds.",
                new String[] {"Cannot lift arms and legs together", "Under 3 seconds", "3 to 9 seconds", "10 seconds"}},
            {"BC_CRAWL", MotorArea.BILATERAL_COORDINATION, "Cross-crawl",
                "Ten cross-crawl movements, opposite hand to opposite knee.",
                new String[] {"Same-side movements only", "Fewer than 5 correct", "5 to 9 correct", "10 correct, rhythmic"}},
            {"BC_CLAP", MotorArea.BILATERAL_COORDINATION, "Clap and jump pattern",
                "Copy a clap-jump-clap pattern three times.",
                new String[] {"Cannot copy the pattern", "Copies once", "Copies twice", "Copies three times"}},
        };
        int position = 1;
        for (Object[] d : defs) {
            String[] levels = (String[]) d[4];
            items.save(new ScreeningItem((String) d[0], position++, (MotorArea) d[1], (String) d[2], (String) d[3],
                    Map.of(0, levels[0], 1, levels[1], 2, levels[2], 3, levels[3])));
        }
    }

    private Map<ColourStage, List<ProgrammeSession>> seedContent() {
        String[][] themes = {
            {"Balance and cross-lateral movement", "Balance, crossing the midline"},
            {"Rolling and rocking", "Body awareness and movement through space"},
            {"Creeping and crawling", "Cross-pattern movement"},
            {"Animal walks", "Strength and coordination"},
            {"Rhythm and rhyme", "Movement to a beat"},
        };
        Map<ColourStage, List<ProgrammeSession>> byStage = new EnumMap<>(ColourStage.class);
        for (ColourStage stage : ColourStage.values()) {
            List<ProgrammeSession> list = new ArrayList<>();
            for (int unit = 1; unit <= 2; unit++) {
                for (int day = 1; day <= 10; day++) {
                    String[] theme = themes[(unit * 10 + day + stage.ordinal()) % themes.length];
                    ProgrammeSession s = new ProgrammeSession(stage, unit, day, theme[0],
                            theme[1] + ". 10 minutes: warm up, 4 activities, cool down.", 10, null,
                            List.of("Warm up: march and swing", "Activity 1: " + theme[0].toLowerCase(),
                                    "Activity 2: starfish stretch", "Activity 3: log roll",
                                    "Activity 4: nursery rhyme actions", "Cool down: slow breathing"));
                    s.review(ContentStatus.APPROVED, "laura@neuroway.test");
                    list.add(content.save(s));
                }
            }
            byStage.put(stage, list);
        }
        // Two drafts awaiting sign-off; the second fails the wording check on purpose.
        content.save(new ProgrammeSession(ColourStage.YELLOW, 3, 1, "Spinning and stopping",
                "Controlled spinning, then stillness.", 10, null,
                List.of("Warm up", "Slow spins", "Statue stop", "Cool down")));
        content.save(new ProgrammeSession(ColourStage.YELLOW, 3, 2, "Helping children with ADHD focus",
                "Movement for children with a diagnosis of ADHD.", 10, null,
                List.of("Warm up", "Wall push", "Cool down")));
        return byStage;
    }

    private void seedScreeningHistory(SchoolClass schoolClass, LocalDate today) {
        List<ScreeningItem> all = items.findAllByOrderByPosition();
        List<Child> pupils = children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(schoolClass.getId());
        LocalDate first = today.minusMonths(6);
        LocalDate second = today.minusDays(20);
        // Leave the last three children unscreened so the screening flow has someone to start with.
        for (int i = 0; i < pupils.size() - 3; i++) {
            Child child = pupils.get(i);
            int base = random.nextInt(2);
            for (ScreeningItem item : all) {
                int s1 = Math.min(3, base + random.nextInt(2));
                results.save(new ScreeningResult(UUID.randomUUID().toString(), child, item, 1, s1, first, "demo"));
                results.save(new ScreeningResult(UUID.randomUUID().toString(), child, item, 2,
                        Math.min(3, s1 + random.nextInt(2)), second, "demo"));
            }
        }
    }

    private void seedTraining(LocalDate today) {
        Course professional = courses.save(new Course(CourseTrack.PROFESSIONAL, "Motor Movers practitioner course",
                "Four weekly live sessions of 90 minutes, recorded modules and short quizzes between sessions, "
                        + "and an assessment. Leads to the Motor Movers practitioner certificate, renewed yearly.",
                4, 90, 18_000, 6_000, true));
        Course parent = courses.save(new Course(CourseTrack.PARENT, "Motor Movers parent course",
                "Three weekly live sessions of 60 minutes with recorded home activities. Builds confidence to run "
                        + "the activities at home.",
                3, 60, 4_500, null, false));
        cohorts.save(new Cohort(professional, "Cohort A · UK evening", today.plusDays(10), "Tuesdays 7:00 pm UK", 30,
                "Pat Preedy", true));
        cohorts.save(new Cohort(professional, "Cohort B · UK morning / AU evening", today.plusDays(24),
                "Wednesdays 9:00 am UK", 30, "Laura Preedy-Maher", false));
        cohorts.save(new Cohort(parent, "Cohort B · three weeks", today.plusDays(7), "Thursdays 7:30 pm UK", 50,
                "Laura Preedy-Maher", true));
    }
}
