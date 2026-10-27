package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.assessment.util.ExtraSupportRule;
import uk.co.motormovers.platform.assessment.util.ExtraSupportRule.Mark;
import uk.co.motormovers.platform.common.model.ColourStage;

class ExtraSupportRuleTest {

    @Test
    void lowOnTheSameExerciseAtTwoPointsInARow() {
        ExtraSupportRule.Result r = ExtraSupportRule.apply(ColourStage.GREEN, List.of(
                new Mark("Starfish", "MODULE_1", 2), new Mark("Starfish", "START", 1),
                new Mark("Starfish", "MODULE_2", 3)));
        assertThat(r.flagged()).isTrue();
        assertThat(r.reasons()).containsExactly("Starfish: 1 at Start, then 2 at End of module 1");
    }

    @Test
    void improvingChildrenAreNotFlagged() {
        assertThat(ExtraSupportRule.apply(ColourStage.GREEN, List.of(
                new Mark("Starfish", "START", 1), new Mark("Starfish", "MODULE_1", 3),
                new Mark("Starfish", "MODULE_2", 2), new Mark("Tortoise", "START", 2))).flagged()).isFalse();
    }

    @Test
    void lowOnHalfTheExercisesAtTheEndOfAModuleOrColour() {
        ExtraSupportRule.Result r = ExtraSupportRule.apply(ColourStage.GREEN, List.of(
                new Mark("Starfish", "MODULE_3", 2), new Mark("Thumbs Up", "MODULE_3", 4),
                new Mark("Starfish", "END", 1), new Mark("Thumbs Up", "END", 2), new Mark("Roundabout", "END", 3)));
        assertThat(r.reasons()).contains("1 or 2 on 1 of 2 exercises at the end of module 3",
                "1 or 2 on 2 of 3 exercises at the end of Green");
    }

    @Test
    void lowStartScoresAloneAreExpected() {
        assertThat(ExtraSupportRule.apply(ColourStage.YELLOW, List.of(new Mark("London Bridge", "START", 1),
                new Mark("Jack and Jill", "START", 1))).flagged()).isFalse();
    }

    @Test
    void manyLowExercisesReadAsOneLine() {
        List<Mark> marks = List.of("Yes and No", "Starfish", "Tortoise", "Big Jump").stream()
                .flatMap(e -> List.of(new Mark(e, "START", 1), new Mark(e, "MODULE_1", 2)).stream()).toList();
        assertThat(ExtraSupportRule.apply(ColourStage.GREEN, marks).reasons()).containsExactly(
                "1 or 2 at two points in a row on 4 exercises, including Yes and No, Starfish and Tortoise",
                "1 or 2 on 4 of 4 exercises at the end of module 1");
    }

    @Test
    void pointsSortAndRead() {
        assertThat(List.of("END", "MODULE_10", "START", "MODULE_2").stream().sorted(ExercisePoints.ORDER).toList())
                .containsExactly("START", "MODULE_2", "MODULE_10", "END");
        assertThat(ExercisePoints.label("END", ColourStage.YELLOW)).isEqualTo("End of Yellow");
        assertThat(ExercisePoints.valid("MODULE_0")).isFalse();
    }
}
