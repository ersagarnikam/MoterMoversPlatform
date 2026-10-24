package uk.co.motormovers.platform.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.model.Trust;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.repository.TrustRepository;
import uk.co.motormovers.platform.school.service.PlanService;
import uk.co.motormovers.platform.screening.model.ScreeningItem;
import uk.co.motormovers.platform.screening.model.ScreeningResult;
import uk.co.motormovers.platform.screening.repository.ScreeningItemRepository;
import uk.co.motormovers.platform.screening.repository.ScreeningResultRepository;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.model.InductionProgress;
import uk.co.motormovers.platform.training.service.LiveSessionService;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
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
    private final CohortRepository cohorts;
    private final EnrolmentRepository enrolments;
    private final LiveSessionService live;
    private final HomeActivityRepository homeActivities;
    private final GuidanceArticleRepository guidance;
    private final ParentLinkRepository parentLinks;
    private final ParentInviteRepository parentInvites;
    private final HomeSessionRecordRepository homeRecords;
    private final LibraryResourceRepository resources;
    private final InductionProgressRepository inductionProgress;
    private final PaymentRepository payments;
    private final PlanService plans;
    private final StarterContent starterContent;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties properties;
    private final Clock clock;
    private final Random random = new Random(42);
    /** Extra parents booked on the live cohort, for deploy/loadtest/live-session.js. Normally 0. */
    private final int loadTestLearners;

    public DemoDataLoader(TrustRepository trusts, SchoolRepository schools, LicenceRepository licences,
            SchoolClassRepository classes, AppUserRepository users, ChildRepository children,
            ProgrammeSessionRepository content, DailySessionRecordRepository records, ScreeningItemRepository items,
            ScreeningResultRepository results, CohortRepository cohorts, EnrolmentRepository enrolments,
            LiveSessionService live,
            HomeActivityRepository homeActivities, GuidanceArticleRepository guidance,
            ParentLinkRepository parentLinks, ParentInviteRepository parentInvites,
            HomeSessionRecordRepository homeRecords, LibraryResourceRepository resources,
            InductionProgressRepository inductionProgress, PaymentRepository payments, PlanService plans,
            StarterContent starterContent, PasswordEncoder passwordEncoder, PlatformProperties properties, Clock clock,
            @Value("${platform.load-test-learners:0}") int loadTestLearners) {
        this.loadTestLearners = loadTestLearners;
        this.payments = payments;
        this.plans = plans;
        this.starterContent = starterContent;
        this.resources = resources;
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
        this.cohorts = cohorts;
        this.enrolments = enrolments;
        this.live = live;
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

        StarterContent.Loaded starter = starterContent.load("laura@neuroway.test");
        seedDrafts();
        Map<ColourStage, List<ProgrammeSession>> sessions = seedContent(starter.rhymes());
        List<InductionLesson> induction = starter.induction();

        Trust trust = trusts.save(new Trust("Northbridge Academy Trust"));
        School oakfield = school("Oakfield Primary", trust, "TRUST",
                EnumSet.range(ColourStage.RED, ColourStage.BLUE), today.minusMonths(3), today.plusMonths(9));
        School marlow = school("Marlow Infants", trust, "TRUST",
                EnumSet.range(ColourStage.RED, ColourStage.YELLOW), today.minusMonths(5), today.plusMonths(7));
        School brookhouse = school("Brookhouse", trust, "TRUST",
                EnumSet.range(ColourStage.RED, ColourStage.GREEN), today.minusMonths(11), today.plusDays(30));
        School ferrers = school("Ferrers Park", trust, "FREE_TRIAL",
                EnumSet.of(ColourStage.RED, ColourStage.ORANGE), today.minusMonths(1), today.plusMonths(2));

        AppUser laura = users.save(new AppUser("admin@motormovers.test", "Laura Preedy-Maher", hash, Role.ADMIN,
                null, null));
        // Pat runs live course sessions on Zoom from the Live sessions page.
        AppUser pat = users.save(new AppUser("pat@motormovers.test", "Pat Preedy", hash, Role.ADMIN, null, null));
        // A trainer in Pat's team: runs the sessions of the cohorts assigned to her, and nothing else.
        AppUser amy = users.save(new AppUser("trainer@motormovers.test", "Amy Clarke", hash, Role.TRAINER, null,
                null));
        AppUser patel = users.save(new AppUser("head@oakfield.test", "Mr Patel", hash, Role.HEAD, oakfield.getId(),
                trust.getId()));
        users.save(new AppUser("trust@northbridge.test", "Ms Okafor", hash, Role.TRUST_LEAD, null, trust.getId()));
        AppUser sam = users.save(new AppUser("practitioner@example.test", "Sam Rivers", hash, Role.PRACTITIONER,
                null, null));
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
        seedCohorts(starter.professional(), starter.parent(), new Trainers(pat, laura, amy), jo, hash, today);
        seedParentApp(oakfield, y2r, jo, today, starter.homeActivities());
        seedPayments(oakfield, marlow, brookhouse, sam, jo, today);
        log.info("Demo data loaded. Sign in with e.g. teacher@oakfield.test / {}", DEMO_PASSWORD);
    }

    private School school(String name, Trust trust, String plan, EnumSet<ColourStage> stages, LocalDate from,
            LocalDate to) {
        School school = schools.save(new School(name, trust, properties.region()));
        licences.save(new Licence(school, plans.find(plan), plans.priceOn(plan, from), from, to, stages));
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
    private void seedParentApp(School oakfield, SchoolClass y2r, AppUser parent, LocalDate today,
            Map<ColourStage, List<HomeActivity>> homeActivities) {
        oakfield.setParentLinksEnabled(true);
        Map<ColourStage, List<HomeActivity>> byStage = homeActivities;

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

    /** Licence invoices for the trust's schools (Brookhouse's renewal still due), two course fees and a book. */
    private void seedPayments(School oakfield, School marlow, School brookhouse, AppUser practitioner,
            AppUser parent, LocalDate today) {
        String team = "admin@motormovers.test";
        int trustLicence = plans.priceOn("TRUST", today);
        List<Payment> list = new ArrayList<>();
        list.add(paid(Payment.bySchool(brookhouse, PaymentPurpose.LICENCE, "Trust licence, year 1", trustLicence,
                today.minusMonths(11), "MM-1001", team, clock.instant()), today.minusMonths(11).plusDays(12)));
        list.add(paid(Payment.bySchool(marlow, PaymentPurpose.LICENCE, "Trust licence, year 1", trustLicence,
                today.minusMonths(5), "MM-1014", team, clock.instant()), today.minusMonths(5).plusDays(20)));
        list.add(paid(Payment.bySchool(oakfield, PaymentPurpose.LICENCE, "Trust licence, year 1", trustLicence,
                today.minusMonths(3), "MM-1020", team, clock.instant()), today.minusMonths(3).plusDays(9)));
        list.add(paid(Payment.bySchool(oakfield, PaymentPurpose.OTHER, "Printed session cards", 6_000,
                today.minusMonths(2), "MM-1026", team, clock.instant()), today.minusMonths(2).plusDays(3)));
        list.add(Payment.bySchool(brookhouse, PaymentPurpose.LICENCE, "Trust licence, year 2", trustLicence,
                today.minusDays(7), "MM-1031", team, clock.instant()));
        list.add(paid(Payment.byPerson(practitioner, PaymentPurpose.COURSE, "Motor Movers practitioner course",
                18_000, today.minusMonths(4), null, team, clock.instant()), today.minusMonths(4)));
        list.add(paid(Payment.byPerson(parent, PaymentPurpose.COURSE, "Motor Movers parent course", 4_500,
                today.minusMonths(1), null, team, clock.instant()), today.minusMonths(1)));
        // Still to pay, so the parent sees what is due.
        list.add(Payment.byPerson(parent, PaymentPurpose.OTHER, "Motor Movers rhyme book and CD", 1_500,
                today.minusDays(5), "MM-1033", team, clock.instant()));
        payments.saveAll(list);
    }

    private static Payment paid(Payment payment, LocalDate on) {
        payment.markPaid(on);
        return payment;
    }



    /** Content awaiting sign-off, so Content approval has something to show. Two fail the wording check. */
    private void seedDrafts() {
        // A rhyme awaiting sign-off.
        resources.save(new LibraryResource(ResourceKind.RHYME, "Five little speckled frogs",
                "Crouching and jumping into the pond, one frog at a time.", ColourStage.RED, null,
                "Five little speckled frogs\nSat on a speckled log\nEating some most delicious bugs\nYum, yum\n"
                        + "One jumped into the pool\nWhere it was nice and cool\n"
                        + "Now there are four green speckled frogs\nGlub, glub"));
        // A draft awaiting sign-off that fails the wording check on purpose.
        homeActivities.save(new HomeActivity(ColourStage.YELLOW, 6, "Exercises for dyspraxia",
                "Help with a diagnosis of dyspraxia at home.", 10, null, List.of("Warm up", "Wall push")));
        guidance.save(new GuidanceArticle("Screen time and active time",
                "Balancing tablets and TV with time on the move.",
                "Draft for Pat and Laura to review.\n\nSuggest a movement break after every programme or game.",
                null, 2));
    }

    /**
     * Three cohorts opening soon, and a parent cohort whose first session starts ten minutes after the demo
     * loads, with Jo booked on it, so joining a live session can be tried straight away.
     */
    private record Trainers(AppUser pat, AppUser laura, AppUser amy) {
    }

    private void seedCohorts(Course professional, Course parent, Trainers t, AppUser jo, String hash,
            LocalDate today) {
        live.schedule(cohort(new Cohort(professional, "Cohort A · UK evening", today.plusDays(10),
                LocalTime.of(19, 0), "Tuesdays 7:00 pm UK", 30, null, true), t.pat()));
        live.schedule(cohort(new Cohort(professional, "Cohort B · UK morning / AU evening", today.plusDays(24),
                LocalTime.of(9, 0), "Wednesdays 9:00 am UK", 30, null, false), t.amy()));
        live.schedule(cohort(new Cohort(parent, "Cohort B · three weeks", today.plusDays(7), LocalTime.of(19, 30),
                "Thursdays 7:30 pm UK", 50, null, true), t.laura()));
        LocalDateTime soon = LocalDateTime.now(clock).plusMinutes(10).truncatedTo(ChronoUnit.MINUTES);
        Cohort now = cohort(new Cohort(parent, "Cohort A · starting now", soon.toLocalDate(),
                soon.toLocalTime(), "Weekly at " + soon.toLocalTime() + " UK", Math.max(50, loadTestLearners + 10),
                null, false), t.pat());
        live.schedule(now);
        enrolments.save(new Enrolment(now, jo));
        // Load tests only: learner001@loadtest.test and on, password "password", all booked on this cohort.
        for (int i = 1; i <= loadTestLearners; i++) {
            AppUser learner = users.save(new AppUser(String.format("learner%03d@loadtest.test", i),
                    "Load test learner " + i, hash, Role.PARENT, null, null));
            enrolments.save(new Enrolment(now, learner));
        }
        if (loadTestLearners > 0) {
            log.info("Load test: {} extra learners booked on {}", loadTestLearners, now.getName());
        }
    }

    private Cohort cohort(Cohort cohort, AppUser trainer) {
        cohort.assignTrainer(trainer);
        return cohorts.save(cohort);
    }
}
