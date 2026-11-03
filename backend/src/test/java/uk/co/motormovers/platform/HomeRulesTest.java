package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.home.util.HomeRules;

class HomeRulesTest {

    @Test
    void youngChildrenStartAtOrangeAndSchoolChildrenAtGreen() {
        assertThat(HomeRules.startingColour(8)).isEqualTo(ColourStage.RED);
        assertThat(HomeRules.startingColour(30)).isEqualTo(ColourStage.ORANGE);
        assertThat(HomeRules.startingColour(47)).isEqualTo(ColourStage.ORANGE);
        assertThat(HomeRules.startingColour(48)).isEqualTo(ColourStage.GREEN);
        // Older children still start at Green, the core programme, rather than the colour for their age.
        assertThat(HomeRules.startingColour(90)).isEqualTo(ColourStage.GREEN);
    }

    @Test
    void theCheckIsDueAtTheStartMiddleAndEndOfEachSchoolYear() {
        LocalDate october = LocalDate.of(2026, 10, 5);
        assertThat(HomeRules.checkDue(null, october)).isTrue();
        assertThat(HomeRules.checkDue(october, LocalDate.of(2026, 12, 20))).isFalse();
        assertThat(HomeRules.checkDue(october, LocalDate.of(2027, 1, 4))).isTrue();
        assertThat(HomeRules.checkDue(october, LocalDate.of(2027, 10, 5))).isTrue();
    }

    @Test
    void modulesComeFromPatsPointsOrOneRhymeEach() {
        List<Exercise> green = List.of(exercise(1, 1, "START", "MODULE_1", "END"),
                exercise(2, 2, "START", "MODULE_1", "MODULE_2", "END"), exercise(3, 3, "START", "MODULE_2", "END"));
        assertThat(HomeRules.modules(green)).extracting(List::size).containsExactly(2, 2);

        List<Exercise> yellow = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            yellow.add(exercise(i, i, "START", "END"));
        }
        assertThat(HomeRules.modules(yellow)).hasSize(7).allSatisfy(m -> assertThat(m).hasSize(1));
    }

    @Test
    void aRhymeIsDoneFiveTimesAWeek() {
        List<Exercise> red = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            red.add(exercise(i, i, "START", "END"));
        }
        HomeRules.Plan first = HomeRules.plan(red, 4);
        assertThat(first.rhymes()).isTrue();
        assertThat(first.today()).extracting(Exercise::getNumber).containsExactly(1);
        assertThat(HomeRules.progressLabel(first)).isEqualTo("Rhyme 1 of 3 · session 5 of 5");
        HomeRules.Plan second = HomeRules.plan(red, 5);
        assertThat(second.today()).extracting(Exercise::getNumber).containsExactly(2);
        assertThat(HomeRules.sessionTitle(second)).isEqualTo("Rhyme 2 · session 1");
        // No module points, so the only rating after the start is at the end of the colour.
        HomeRules.Plan done = HomeRules.plan(red, 15);
        assertThat(done.colourComplete()).isTrue();
        assertThat(HomeRules.progressLabel(done)).isEqualTo("All 3 rhymes done: keep practising");
        assertThat(HomeRules.reviewDue(red, done, Set.of()).point()).isEqualTo("END");
    }

    @Test
    void aModuleBuildsUpOverFiveWeeks() {
        // Seven exercises in module 1, as in Pat's Green example: two in week 1, then one or two added each week.
        List<Exercise> green = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            green.add(exercise(i, i, "START", "MODULE_1", "END"));
        }
        green.add(exercise(8, 8, "START", "MODULE_2", "END"));
        HomeRules.Plan week1 = HomeRules.plan(green, 0);
        assertThat(week1.today()).extracting(Exercise::getNumber).containsExactly(1, 2);
        assertThat(week1.newThisWeek()).isEmpty();
        assertThat(HomeRules.progressLabel(week1)).isEqualTo("Module 1 of 2 · week 1 of 5");
        HomeRules.Plan week2 = HomeRules.plan(green, 5);
        assertThat(week2.today()).extracting(Exercise::getNumber).containsExactly(1, 2, 3, 4);
        assertThat(week2.newThisWeek()).extracting(Exercise::getNumber).containsExactly(3, 4);
        HomeRules.Plan week5 = HomeRules.plan(green, 24);
        assertThat(week5.week()).isEqualTo(5);
        assertThat(week5.today()).hasSize(7);
        assertThat(week5.newThisWeek()).extracting(Exercise::getNumber).containsExactly(7);
    }

    @Test
    void exercisesCarriedIntoTheNextModuleAreThereFromItsFirstWeek() {
        List<Exercise> green = List.of(exercise(1, 1, "START", "MODULE_1", "MODULE_2", "END"),
                exercise(2, 2, "START", "MODULE_1", "END"), exercise(3, 3, "START", "MODULE_2", "END"),
                exercise(4, 4, "START", "MODULE_2", "END"), exercise(5, 5, "START", "MODULE_2", "END"));
        HomeRules.Plan module2 = HomeRules.plan(green, 25);
        assertThat(module2.module()).isEqualTo(2);
        assertThat(module2.today()).extracting(Exercise::getNumber).containsExactly(1, 3, 4);
    }

    @Test
    void theReviewsDueFollowTheModulesDone() {
        List<Exercise> green = List.of(exercise(1, 1, "START", "MODULE_1", "END"),
                exercise(2, 2, "START", "MODULE_2", "END"));
        HomeRules.Plan start = HomeRules.plan(green, 0);
        assertThat(start.module()).isEqualTo(1);
        assertThat(start.sessionInModule()).isEqualTo(1);
        assertThat(HomeRules.reviewDue(green, start, Set.of())).isNull();

        HomeRules.Plan next = HomeRules.plan(green, 27);
        assertThat(next.module()).isEqualTo(2);
        assertThat(next.sessionInModule()).isEqualTo(3);
        assertThat(HomeRules.reviewDue(green, next, Set.of()).point()).isEqualTo("MODULE_1");
        assertThat(HomeRules.reviewDue(green, next, Set.of("1:MODULE_1"))).isNull();

        HomeRules.Plan done = HomeRules.plan(green, 50);
        assertThat(done.colourComplete()).isTrue();
        assertThat(HomeRules.reviewDue(green, done, Set.of("1:MODULE_1", "2:MODULE_2")).point()).isEqualTo("END");
    }

    private static Exercise exercise(long id, int number, String... points) {
        Exercise e = new Exercise(ColourStage.GREEN, number, "Exercise " + number, "", Map.of(), List.of(points));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
