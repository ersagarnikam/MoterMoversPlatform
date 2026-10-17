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

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.classroom.model.DailySessionRecord;
import uk.co.motormovers.platform.classroom.repository.DailySessionRecordRepository;
import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.util.SchoolDays;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.GuidanceArticle;
import uk.co.motormovers.platform.content.model.HomeActivity;
import uk.co.motormovers.platform.content.model.LibraryResource;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.model.ResourceKind;
import uk.co.motormovers.platform.content.repository.GuidanceArticleRepository;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.family.model.HomeSessionRecord;
import uk.co.motormovers.platform.family.model.ParentInvite;
import uk.co.motormovers.platform.family.model.ParentLink;
import uk.co.motormovers.platform.family.repository.HomeSessionRecordRepository;
import uk.co.motormovers.platform.family.repository.ParentInviteRepository;
import uk.co.motormovers.platform.family.repository.ParentLinkRepository;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.LicencePlan;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.model.Trust;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.repository.TrustRepository;
import uk.co.motormovers.platform.screening.model.MotorArea;
import uk.co.motormovers.platform.screening.model.ScreeningItem;
import uk.co.motormovers.platform.screening.model.ScreeningResult;
import uk.co.motormovers.platform.screening.repository.ScreeningItemRepository;
import uk.co.motormovers.platform.screening.repository.ScreeningResultRepository;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.model.InductionProgress;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.repository.InductionProgressRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Example data for local development and demos. Names, schools and figures are examples only,
 * matching the sketches in the platform overview. Every demo account uses the password "password".
 */
@Component
@ConditionalOnProperty(name = "platform.seed-demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    public static final String DEMO_PASSWORD = "password";
    /** An unused invite for a second child in Ms Hardy's class. Parents type it as HOME-TIME. */
    public static final String DEMO_INVITE_CODE = "HOMETIME";

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
    private final HomeActivityRepository homeActivities;
    private final GuidanceArticleRepository guidance;
    private final ParentLinkRepository parentLinks;
    private final ParentInviteRepository parentInvites;
    private final HomeSessionRecordRepository homeRecords;
    private final LibraryResourceRepository resources;
    private final InductionLessonRepository inductionLessons;
    private final InductionProgressRepository inductionProgress;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties properties;
    private final Clock clock;
    private final Random random = new Random(42);

    public DemoDataLoader(TrustRepository trusts, SchoolRepository schools, LicenceRepository licences,
            SchoolClassRepository classes, AppUserRepository users, ChildRepository children,
            ProgrammeSessionRepository content, DailySessionRecordRepository records, ScreeningItemRepository items,
            ScreeningResultRepository results, CourseRepository courses, CohortRepository cohorts,
            HomeActivityRepository homeActivities, GuidanceArticleRepository guidance,
            ParentLinkRepository parentLinks, ParentInviteRepository parentInvites,
            HomeSessionRecordRepository homeRecords, LibraryResourceRepository resources,
            InductionLessonRepository inductionLessons, InductionProgressRepository inductionProgress,
            PasswordEncoder passwordEncoder, PlatformProperties properties, Clock clock) {
        this.resources = resources;
        this.inductionLessons = inductionLessons;
        this.inductionProgress = inductionProgress;
        this.homeActivities = homeActivities;
        this.guidance = guidance;
        this.parentLinks = parentLinks;
        this.parentInvites = parentInvites;
        this.homeRecords = homeRecords;
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
        Map<ColourStage, List<LibraryResource>> rhymes = seedResources();
        Map<ColourStage, List<ProgrammeSession>> sessions = seedContent(rhymes);
        List<InductionLesson> induction = seedInduction();

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
        AppUser patel = users.save(new AppUser("head@oakfield.test", "Mr Patel", hash, Role.HEAD, oakfield.getId(),
                trust.getId()));
        users.save(new AppUser("trust@northbridge.test", "Ms Okafor", hash, Role.TRUST_LEAD, null, trust.getId()));
        users.save(new AppUser("practitioner@example.test", "Sam Rivers", hash, Role.PRACTITIONER, null, null));
        AppUser jo = users.save(new AppUser("parent@example.test", "Jo Bennett", hash, Role.PARENT, null, null));
        AppUser hardy = users.save(new AppUser("teacher@oakfield.test", "Ms Hardy", hash, Role.TEACHER,
                oakfield.getId(), trust.getId()));
        // A newly qualified teacher who has not done the induction yet, so their sessions are still locked.
        AppUser lewis = users.save(new AppUser("newteacher@oakfield.test", "Mr Lewis", hash, Role.TEACHER,
                oakfield.getId(), trust.getId()));
        for (AppUser trained : List.of(hardy, patel)) {
            induction.forEach(l -> inductionProgress.save(new InductionProgress(trained.getId(), l.getId())));
            trained.completeInduction(today.minusMonths(4));
        }

        List<SchoolClass> screenedClasses = new ArrayList<>();
        screenedClasses.add(classWithHistory(oakfield, "Reception A", "Reception", null, ColourStage.RED, 26, 18,
                sessions, today));
        screenedClasses.add(classWithHistory(oakfield, "Year 1 M", "Year 1", null, ColourStage.ORANGE, 27, 16,
                sessions, today));
        SchoolClass y2r = classWithHistory(oakfield, "Year 2 R", "Year 2", hardy, ColourStage.YELLOW, 28, 17,
                sessions, today);
        screenedClasses.add(y2r);
        screenedClasses.add(classWithHistory(oakfield, "Year 2 T", "Year 2", lewis, ColourStage.YELLOW, 27, 12,
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
        seedParentApp(oakfield, y2r, jo, today);
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

    private Map<ColourStage, List<ProgrammeSession>> seedContent(Map<ColourStage, List<LibraryResource>> rhymes) {
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
                    List<LibraryResource> stageRhymes = rhymes.getOrDefault(stage, List.of());
                    if (!stageRhymes.isEmpty()) {
                        s.setRhyme(stageRhymes.get(day % stageRhymes.size()));
                    }
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

    /**
     * Traditional nursery rhymes for the Red, Orange and Yellow stages, the rhyme book and two printables.
     * There are no recordings yet, so every file URL is empty. Returns the approved rhymes by stage.
     */
    private Map<ColourStage, List<LibraryResource>> seedResources() {
        Object[][] rhymeDefs = {
            {ColourStage.RED, "Row, row, row your boat", "Rocking forwards and backwards in pairs.",
                "Row, row, row your boat\nGently down the stream\nMerrily, merrily, merrily, merrily\n"
                        + "Life is but a dream"},
            {ColourStage.RED, "Humpty Dumpty", "Sitting tall, then a slow roll to the side.",
                "Humpty Dumpty sat on a wall\nHumpty Dumpty had a great fall\nAll the king's horses and all the "
                        + "king's men\nCouldn't put Humpty together again"},
            {ColourStage.ORANGE, "Incy Wincy Spider", "Finger climbing and big arm movements.",
                "Incy Wincy spider climbed up the water spout\nDown came the rain and washed the spider out\n"
                        + "Out came the sunshine and dried up all the rain\n"
                        + "So Incy Wincy spider climbed up the spout again"},
            {ColourStage.ORANGE, "The grand old Duke of York", "Marching, reaching up high and crouching low.",
                "Oh, the grand old Duke of York\nHe had ten thousand men\nHe marched them up to the top of the hill\n"
                        + "And he marched them down again\nAnd when they were up, they were up\n"
                        + "And when they were down, they were down\n"
                        + "And when they were only halfway up\nThey were neither up nor down"},
            {ColourStage.YELLOW, "Jack and Jill", "Climbing on the spot, then a forward roll on the mat.",
                "Jack and Jill went up the hill\nTo fetch a pail of water\nJack fell down and broke his crown\n"
                        + "And Jill came tumbling after"},
            {ColourStage.YELLOW, "Hickory dickory dock", "Swinging arms like a pendulum and crossing the middle.",
                "Hickory dickory dock\nThe mouse ran up the clock\nThe clock struck one\nThe mouse ran down\n"
                        + "Hickory dickory dock"},
        };
        Map<ColourStage, List<LibraryResource>> byStage = new EnumMap<>(ColourStage.class);
        for (Object[] d : rhymeDefs) {
            LibraryResource r = new LibraryResource(ResourceKind.RHYME, (String) d[1], (String) d[2],
                    (ColourStage) d[0], null, (String) d[3]);
            r.review(ContentStatus.APPROVED, "laura@neuroway.test");
            byStage.computeIfAbsent((ColourStage) d[0], s -> new ArrayList<>()).add(resources.save(r));
        }

        Object[][] files = {
            {ResourceKind.BOOK, "Motor Movers rhyme book",
                "Every rhyme from the Red, Orange and Yellow stages, illustrated, with the actions for each line."},
            {ResourceKind.PRINTABLE, "Getting the space ready",
                "A one-page checklist: clear floor, shoes and socks off, jumpers and ties off, music ready."},
            {ResourceKind.PRINTABLE, "Letter home: starting Motor Movers",
                "A letter for families explaining the daily sessions and how to join in with the parent app."},
        };
        for (Object[] d : files) {
            LibraryResource r = new LibraryResource((ResourceKind) d[0], (String) d[1], (String) d[2], null, null,
                    null);
            r.review(ContentStatus.APPROVED, "laura@neuroway.test");
            resources.save(r);
        }
        // A rhyme awaiting sign-off.
        resources.save(new LibraryResource(ResourceKind.RHYME, "Five little speckled frogs",
                "Crouching and jumping into the pond, one frog at a time.", ColourStage.RED, null,
                "Five little speckled frogs\nSat on a speckled log\nEating some most delicious bugs\nYum, yum\n"
                        + "One jumped into the pool\nWhere it was nice and cool\n"
                        + "Now there are four green speckled frogs\nGlub, glub"));
        return byStage;
    }

    /** The one-hour induction: six approved lessons of ten minutes. */
    private List<InductionLesson> seedInduction() {
        Object[][] defs = {
            {"Why movement comes first", "The early movements that get a body ready for the classroom.",
                "Babies are meant to spend a long time on the floor: lifting the head, rolling, creeping on the "
                        + "tummy, crawling on hands and knees, then standing and walking. Each stage builds the "
                        + "balance and body control a child later needs to sit still, look at a page and hold a "
                        + "pencil.\n\n"
                        + "Car seats, buggies, baby seats and screens mean many children have had far less of this "
                        + "than they need. Motor Movers gives them a second chance at those movements, a little "
                        + "every day, as part of school life."},
            {"Early reflexes and learning", "Why some baby reflexes matter in Reception and Key Stage 1.",
                "Babies are born with reflexes that help them survive, such as the grasp reflex, where the hand "
                        + "closes when the palm is touched, and the reflex that straightens the arm on the side "
                        + "the head turns towards. Rolling and crawling help these reflexes fade during the first "
                        + "year or so, and more mature balance takes over.\n\n"
                        + "When an early reflex lingers, a child may find some things harder than they should be: "
                        + "a comfortable pencil grip, reading smoothly across the middle of the page, or writing "
                        + "up to a margin. The programme repeats the movements that help these reflexes fade."},
            {"The seven colour stages", "Matching the programme to where children are.",
                "The programme has seven stages following the colours of the rainbow, from Red to Violet. Each "
                        + "class works at one stage, chosen from screening, so the movements match what the "
                        + "children are ready for rather than their age.\n\n"
                        + "Red, Orange and Yellow combine movement with nursery rhymes. You will find each rhyme's "
                        + "recording and words on the session screen and in Resources, along with the rhyme book."},
            {"Running the daily session", "Ten minutes, every school day, with the whole class.",
                "Clear a space, take off shoes, socks, jumpers and ties, and press Start session. The screen "
                        + "leads the class through a warm up, four activities and a cool down. The session is in "
                        + "addition to PE, not part of it.\n\n"
                        + "At the end, tap any child who was absent and press Complete session. The half-term "
                        + "counter on the Today screen shows how many school days the class has managed. Little "
                        + "and often is what makes the difference."},
            {"Screening and the readiness profile", "What the scores tell you, and what they do not.",
                "Screen each child at the start and again later in the year, one item at a time on a 0 to 3 "
                        + "scale. The readiness profile shows six areas as Emerging, Developing or Secure and "
                        + "suggests a colour stage.\n\n"
                        + "The profile describes movement readiness only. If a child is not moving on after a "
                        + "good run of daily sessions, share what you have seen with your SENCo, who can decide "
                        + "on next steps."},
            {"Keeping children and families safe", "Names, video, parents and the words we use.",
                "The platform shows initials and year group unless your school switches full names on, never "
                        + "records dates of birth, and keeps video off unless the school and each family agree.\n\n"
                        + "When you talk to families, describe what you see: \"finds balancing on one leg tricky\" "
                        + "rather than a label. If a parent wants the app, create a parent code from the child's "
                        + "readiness profile. Codes work once and expire after 14 days."},
        };
        List<InductionLesson> lessons = new ArrayList<>();
        int position = 1;
        for (Object[] d : defs) {
            InductionLesson l = new InductionLesson(position++, (String) d[0], (String) d[1], (String) d[2], 10, null);
            l.review(ContentStatus.APPROVED, "laura@neuroway.test");
            lessons.add(inductionLessons.save(l));
        }
        return lessons;
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

    /**
     * Oakfield has parent links switched on. Jo Bennett is linked to a child in Ms Hardy's class and has
     * done some home activities; a second invite code, HOME-TIME, is ready to redeem in the demo.
     */
    private void seedParentApp(School oakfield, SchoolClass y2r, AppUser parent, LocalDate today) {
        oakfield.setParentLinksEnabled(true);
        Map<ColourStage, List<HomeActivity>> byStage = seedHomeActivities();
        seedGuidance();

        List<Child> roster = children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(y2r.getId());
        Child child = roster.getFirst();
        parentLinks.save(new ParentLink(parent, child));
        parentInvites.save(new ParentInvite(roster.get(1), DEMO_INVITE_CODE, "teacher@oakfield.test",
                today.plusDays(14)));

        // Leave today undone so the parent can complete it in the demo.
        List<HomeActivity> sequence = byStage.get(child.getCurrentStage());
        int n = 0;
        for (int daysAgo : new int[] {12, 9, 7, 5, 4, 2, 1}) {
            homeRecords.save(new HomeSessionRecord(UUID.randomUUID().toString(), parent.getId(), child.getId(),
                    sequence.get(n++ % sequence.size()), today.minusDays(daysAgo)));
        }
    }

    private Map<ColourStage, List<HomeActivity>> seedHomeActivities() {
        Object[][] defs = {
            {"Starfish and seed", "Stretching out and curling up: body awareness", 5, new String[] {
                "Lie on your backs side by side, arms and legs out wide like a starfish",
                "Curl up small into a seed and hold for a count of five",
                "Stretch back out slowly. Repeat five times",
                "Finish lying still and breathing slowly together"}},
            {"Animal walk parade", "Moving on hands and feet for strength and coordination", 8, new String[] {
                "Bear walk across the room on hands and feet",
                "Crab walk back, tummy up",
                "Frog jumps to the door and back",
                "Choose a favourite animal and do it once more, slowly"}},
            {"Cushion path", "Balance on a wobbly path", 6, new String[] {
                "Lay a line of cushions or folded towels on the floor",
                "Walk along the path without stepping off",
                "Try it again, walking backwards with a grown-up's hand",
                "Finish by standing on one cushion on one leg, then the other"}},
            {"Sock ball catch", "Aiming and catching with a soft ball", 6, new String[] {
                "Roll a pair of socks into a ball",
                "Stand two big steps apart and throw underarm to each other",
                "Count how many catches you make in a row",
                "Try throwing into a basket or box from the same spot"}},
            {"Cross-crawl march", "Crossing the middle of the body", 5, new String[] {
                "March on the spot, lifting knees high",
                "Touch each knee with the opposite hand as it comes up",
                "Do ten slowly, then ten a bit faster",
                "Finish with ten slow ones to a favourite song"}},
            {"Rocking boat", "Rocking and rolling for balance", 5, new String[] {
                "Sit on the floor and hug your knees",
                "Rock gently backwards and forwards like a boat",
                "Rock side to side without letting go",
                "Lie down and roll slowly like a log to finish"}},
            {"Aeroplane", "Lying on the tummy, arms and legs lifted, for a strong back", 5, new String[] {
                "Lie on your tummies facing each other",
                "Lift arms and legs off the floor like an aeroplane",
                "Hold while you count to five, then rest",
                "Repeat three times and see if the count can grow"}},
        };
        Map<ColourStage, List<HomeActivity>> byStage = new EnumMap<>(ColourStage.class);
        for (ColourStage stage : ColourStage.values()) {
            List<HomeActivity> list = new ArrayList<>();
            for (int position = 1; position <= 5; position++) {
                Object[] d = defs[(stage.ordinal() + position - 1) % defs.length];
                HomeActivity a = new HomeActivity(stage, position, (String) d[0], (String) d[1], (Integer) d[2],
                        null, List.of((String[]) d[3]));
                a.review(ContentStatus.APPROVED, "laura@neuroway.test");
                list.add(homeActivities.save(a));
            }
            byStage.put(stage, list);
        }
        // A draft awaiting sign-off that fails the wording check on purpose.
        homeActivities.save(new HomeActivity(ColourStage.YELLOW, 6, "Exercises for dyspraxia",
                "Help with a diagnosis of dyspraxia at home.", 10, null, List.of("Warm up", "Wall push")));
        return byStage;
    }

    private void seedGuidance() {
        Object[][] approved = {
            {"Why movement comes before pencil skills",
                "Steady sitting and a strong core make writing and drawing easier.", null, 3,
                "Before a child can hold a pencil comfortably, they need a body that stays steady while their "
                        + "hand works. Crawling, rolling, climbing and balancing all build that steadiness.\n\n"
                        + "The Motor Movers sessions at school follow the same order: big movements first, small "
                        + "ones later. A few minutes of play at home each day helps the same skills grow.\n\n"
                        + "You do not need equipment. Cushions, socks, a hallway and some music are plenty."},
            {"Little and often",
                "Five minutes most days does more than an hour once a week.", null, 2,
                "Children build movement skills through repetition. A short, cheerful session most days "
                        + "works better than a long one now and then.\n\n"
                        + "Try linking the home activity to something that already happens every day, such as "
                        + "after tea or before the bath. The app counts the days for you.\n\n"
                        + "If a day is missed, simply pick it up again tomorrow."},
            {"Making space for movement at home",
                "Small homes work well. Here is how to set up safely.", null, 2,
                "Clear a strip of floor about two metres long. Move anything sharp or breakable, and use a rug "
                        + "or towel on hard floors.\n\n"
                        + "Bare feet are best for balance activities, as long as the floor is safe.\n\n"
                        + "Join in. Children copy what they see, and the activities are more fun together."},
            {"Getting the most from the parent course",
                "Three live sessions with the Motor Movers team.", null, 2,
                "The parent course runs as three live online sessions of an hour each. Only adults are on "
                        + "camera.\n\n"
                        + "The trainers show each activity and explain why it helps. Questions about one "
                        + "particular child are best taken to your child's school or your GP."},
            {"Rolling, rocking and crawling",
                "Why the early stages spend so long on the floor.", ColourStage.RED, 3,
                "The first stages of the programme spend a lot of time rolling, rocking and crawling. These "
                        + "movements help a child understand where their body is and how its two sides work "
                        + "together.\n\n"
                        + "At home, a rug and a bit of space are all you need. Make it a game: roll like a log, "
                        + "rock like a boat, crawl through a tunnel of chairs."},
            {"Balance games for the middle stages",
                "Simple ways to practise balance on the way to school.", ColourStage.YELLOW, 2,
                "Walking along a kerb line holding your hand, stepping only on the paving slabs, or standing "
                        + "like a flamingo while you wait for the bus all count as balance practice.\n\n"
                        + "Keep it light and stop while it is still fun."},
        };
        for (Object[] d : approved) {
            GuidanceArticle a = new GuidanceArticle((String) d[0], (String) d[1], (String) d[4], (ColourStage) d[2],
                    (Integer) d[3]);
            a.review(ContentStatus.APPROVED, "laura@neuroway.test");
            guidance.save(a);
        }
        guidance.save(new GuidanceArticle("Screen time and active time",
                "Balancing tablets and TV with time on the move.",
                "Draft for Pat and Laura to review.\n\nSuggest a movement break after every programme or game.",
                null, 2));
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
