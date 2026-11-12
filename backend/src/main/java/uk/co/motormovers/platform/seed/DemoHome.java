package uk.co.motormovers.platform.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.ExerciseScoreRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneAchievementRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.assessment.service.AssessmentContent;
import uk.co.motormovers.platform.assessment.util.SchoolYear;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.home.model.Family;
import uk.co.motormovers.platform.home.model.FamilyMember;
import uk.co.motormovers.platform.home.model.HomePass;
import uk.co.motormovers.platform.home.model.HomePassPrice;
import uk.co.motormovers.platform.home.model.HomeProgrammeSession;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.home.repository.FamilyRepository;
import uk.co.motormovers.platform.home.repository.HomePassPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassRepository;
import uk.co.motormovers.platform.home.repository.HomeProgrammeSessionRepository;
import uk.co.motormovers.platform.home.service.HomePassService;
import uk.co.motormovers.platform.home.util.HomeRules;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.user.model.AppUser;

/**
 * A demo family on Motor Movers at Home, not linked to a school: Priya Shah (home@example.test) with Ava, 50 months
 * (started at Green, as children at school do), who did the starting check six weeks ago and is in her second Green
 * module (module 1's review is due), and Leo, 20 months, who has not done the check yet.
 */
@Component
class DemoHome {

    /** Sessions Ava has done: module 1 (five weeks of five) and two of module 2. */
    static final int AVA_SESSIONS = HomeRules.WEEKS_PER_MODULE * HomeRules.SESSIONS_PER_WEEK + 2;

    static final int AVA_AGE = 50;

    private final AssessmentContent content;
    private final SchoolYear schoolYear;
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final ChildRepository children;
    private final MilestoneRepository milestones;
    private final MilestoneAchievementRepository achievements;
    private final ExerciseRepository exercises;
    private final ExerciseScoreRepository scores;
    private final HomeProgrammeSessionRepository sessions;
    private final HomePassPriceRepository passPrices;
    private final HomePassRepository passes;
    private final PaymentRepository payments;
    private final CountrySettings country;

    /** A placeholder price for the demo until Kay's is set (go-live decision 3). */
    static final int DEMO_PASS_PENCE = 3500;

    DemoHome(AssessmentContent content, FamilyRepository families, FamilyMemberRepository members,
            ChildRepository children, MilestoneRepository milestones, MilestoneAchievementRepository achievements,
            ExerciseRepository exercises, ExerciseScoreRepository scores, HomeProgrammeSessionRepository sessions,
            SchoolYear schoolYear, HomePassPriceRepository passPrices, HomePassRepository passes,
            PaymentRepository payments, CountrySettings country) {
        this.passPrices = passPrices;
        this.passes = passes;
        this.payments = payments;
        this.country = country;
        this.content = content;
        this.schoolYear = schoolYear;
        this.families = families;
        this.members = members;
        this.children = children;
        this.milestones = milestones;
        this.achievements = achievements;
        this.exercises = exercises;
        this.scores = scores;
        this.sessions = sessions;
    }

    void seed(AppUser parent, LocalDate today) {
        content.loadMissing();
        Family family = families.save(new Family());
        members.save(new FamilyMember(family.getId(), parent.getId(), FamilyMember.Role.OWNER));

        LocalDate checked = today.minusDays(42);
        passPrices.save(new HomePassPrice(DEMO_PASS_PENCE, today.minusYears(2), "admin@motormovers.test",
                Instant.now()));
        // Priya bought the family's pass by card the day she did Ava's check.
        pass(parent, family, checked);
        // Priya is past the parent induction; a family signing up in the demo sees it first.
        parent.completeInduction(checked);
        Child ava = Child.atHome(family.getId(), "Ava", AVA_AGE, checked);
        ava.placeAt(ColourStage.GREEN);
        ava.checkedOn(checked);
        children.save(ava);
        // The check: movements up to her age are secure, a few of the last year's ones not yet.
        int i = 0;
        for (var m : milestones.findAllByOrderByChartAscPositionAsc()) {
            if (m.getAgeToMonths() <= AVA_AGE - 12 || (m.getAgeFromMonths() <= AVA_AGE && i++ % 3 != 0)) {
                achievements.save(new MilestoneAchievement(UUID.randomUUID().toString(), ava, m, AVA_AGE,
                        schoolYear.pointOn(checked), schoolYear.yearOf(checked), checked, parent.getEmail()));
            }
        }
        // Module 1's sessions and two of module 2, most days since the check.
        for (int n = 0; n < AVA_SESSIONS; n++) {
            sessions.save(new HomeProgrammeSession(UUID.randomUUID().toString(), ava.getId(), ColourStage.GREEN,
                    checked.plusDays(1 + n + n / 4), parent.getEmail()));
        }
        // Rated the first time she did each exercise of module 1; the end-of-module review is still to do.
        List<Exercise> module1 = HomeRules.modules(exercises.findByStageOrderByNumber(ColourStage.GREEN)).getFirst();
        for (Exercise e : module1) {
            scores.save(new ExerciseScore(UUID.randomUUID().toString(), ava, e, "START", 1 + e.getNumber() % 3,
                    checked.plusDays(1), parent.getEmail()));
        }

        children.save(Child.atHome(family.getId(), "Leo", 20, today));
    }

    /** A family whose pass ended eleven days ago: Tom Reid (lapsed@example.test) with Noah, 40 months, at Orange. */
    void seedEnded(AppUser parent, LocalDate today) {
        Family family = families.save(new Family());
        members.save(new FamilyMember(family.getId(), parent.getId(), FamilyMember.Role.OWNER));
        LocalDate bought = today.minusYears(1).minusDays(10);
        parent.completeInduction(bought);
        Child noah = Child.atHome(family.getId(), "Noah", 40, bought);
        noah.placeAt(ColourStage.ORANGE);
        noah.checkedOn(bought);
        children.save(noah);
        pass(parent, family, bought);
    }

    /** A pass paid by card on a day: twelve months from it. */
    private void pass(AppUser parent, Family family, LocalDate paidOn) {
        Payment payment = Payment.byPerson(parent, PaymentPurpose.HOME_PASS, HomePassService.DESCRIPTION,
                DEMO_PASS_PENCE, paidOn, null, parent.getEmail(), Instant.now());
        payment.includesTaxAt(country.taxRateBp());
        payment.paidOnline(paidOn, "pi_demo_" + parent.getId());
        payment = payments.save(payment);
        passes.save(new HomePass(family.getId(), payment.getId(), parent.getEmail(), Instant.now(), null)
                .runs(paidOn, paidOn.plusYears(1).minusDays(1)));
    }
}
