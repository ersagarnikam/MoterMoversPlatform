package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

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
    void eachAgeHasAColour() {
        assertThat(HomeRules.colourForAge(3)).isEqualTo(ColourStage.RED);
        assertThat(HomeRules.colourForAge(20)).isEqualTo(ColourStage.ORANGE);
        assertThat(HomeRules.colourForAge(44)).isEqualTo(ColourStage.GREEN);
        assertThat(HomeRules.colourForAge(100)).isEqualTo(ColourStage.VIOLET);
    }

    @Test
    void mostlyNotYetSuggestsTheColourBefore() {
        assertThat(HomeRules.suggest(44, 2, 6).colour()).isEqualTo(ColourStage.YELLOW);
        assertThat(HomeRules.suggest(44, 6, 2).colour()).isEqualTo(ColourStage.GREEN);
        // Too few answers to go on, and never below Red.
        assertThat(HomeRules.suggest(44, 0, 3).colour()).isEqualTo(ColourStage.GREEN);
        assertThat(HomeRules.suggest(8, 0, 8).colour()).isEqualTo(ColourStage.RED);
    }

    @Test
    void modulesComeFromPatsPointsOrGroupsOfThree() {
        List<Exercise> green = List.of(exercise(1, 1, "START", "MODULE_1", "END"),
                exercise(2, 2, "START", "MODULE_1", "MODULE_2", "END"), exercise(3, 3, "START", "MODULE_2", "END"));
        assertThat(HomeRules.modules(green)).extracting(List::size).containsExactly(2, 2);

        List<Exercise> yellow = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            yellow.add(exercise(i, i, "START", "END"));
        }
        assertThat(HomeRules.modules(yellow)).extracting(List::size).containsExactly(3, 3, 1);
    }

    @Test
    void theSessionMovesOnEveryTenAndAsksForTheReviewsDue() {
        List<Exercise> green = List.of(exercise(1, 1, "START", "MODULE_1", "END"),
                exercise(2, 2, "START", "MODULE_2", "END"));
        HomeRules.Plan start = HomeRules.plan(green, 0);
        assertThat(start.module()).isEqualTo(1);
        assertThat(start.sessionInModule()).isEqualTo(1);
        assertThat(HomeRules.reviewDue(green, start, Set.of())).isNull();

        HomeRules.Plan next = HomeRules.plan(green, 12);
        assertThat(next.module()).isEqualTo(2);
        assertThat(next.sessionInModule()).isEqualTo(3);
        assertThat(HomeRules.reviewDue(green, next, Set.of()).point()).isEqualTo("MODULE_1");
        assertThat(HomeRules.reviewDue(green, next, Set.of("1:MODULE_1"))).isNull();

        HomeRules.Plan done = HomeRules.plan(green, 25);
        assertThat(done.colourComplete()).isTrue();
        assertThat(HomeRules.reviewDue(green, done, Set.of("1:MODULE_1", "2:MODULE_2")).point()).isEqualTo("END");
    }

    private static Exercise exercise(long id, int number, String... points) {
        Exercise e = new Exercise(ColourStage.GREEN, number, "Exercise " + number, "", Map.of(), List.of(points));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }
}
