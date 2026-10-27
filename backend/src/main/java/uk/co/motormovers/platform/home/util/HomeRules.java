package uk.co.motormovers.platform.home.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * The rules of Motor Movers at Home. Every number here is a suggested default for Pat to confirm
 * (docs/home-programme-design.md): the age each colour suits, which movements the starting check asks about, when it
 * suggests starting a colour earlier, and how many sessions make a module.
 */
public final class HomeRules {

    /** The youngest age (months) a colour suits; the programme covers 6 months to 8 years. Pat to confirm. */
    static final int[] COLOUR_FROM_MONTHS = {0, 18, 30, 42, 54, 66, 78};

    /** The check asks about movements from the year before the child's age up to their age. */
    static final int CHECK_LOOKBACK_MONTHS = 12;

    /** With fewer than half the answered movements secure, the check suggests the colour before. */
    static final double SECURE_SHARE_TO_STAY = 0.5;

    /** Below this many Yes or Not yet answers the check keeps the colour for the child's age. */
    static final int MIN_ANSWERS = 4;

    public static final int SESSIONS_PER_MODULE = 10;

    public static final int EXERCISES_PER_SESSION = 4;

    /** Colours without modules in Pat's grid (Yellow) are worked through in groups of this many exercises. */
    static final int GROUP_SIZE = 3;

    /** The starting check is suggested again after this many days. */
    public static final int RECHECK_AFTER_DAYS = 91;

    private HomeRules() {
    }

    /** The colour for a child's age, before the check. */
    public static ColourStage colourForAge(int ageMonths) {
        ColourStage colour = ColourStage.RED;
        for (ColourStage s : ColourStage.values()) {
            if (ageMonths >= COLOUR_FROM_MONTHS[s.ordinal()]) {
                colour = s;
            }
        }
        return colour;
    }

    /** The movements the starting check asks about: those a child usually does in the year up to their age. */
    public static boolean askAbout(Milestone m, int ageMonths) {
        return m.getAgeFromMonths() <= ageMonths && m.getAgeToMonths() >= ageMonths - CHECK_LOOKBACK_MONTHS;
    }

    public record Suggestion(ColourStage colour, ColourStage forAge, boolean earlier) {
    }

    /** The colour to start at: the one for the child's age, or the one before when most answers are "Not yet". */
    public static Suggestion suggest(int ageMonths, int yes, int notYet) {
        ColourStage forAge = colourForAge(ageMonths);
        int answered = yes + notYet;
        boolean earlier = answered >= MIN_ANSWERS && (double) yes / answered < SECURE_SHARE_TO_STAY
                && forAge != ColourStage.RED;
        return new Suggestion(earlier ? ColourStage.values()[forAge.ordinal() - 1] : forAge, forAge, earlier);
    }

    /** Where a child is in a colour: module, session within it, and the exercises for today. */
    public record Plan(int module, int modules, int sessionInModule, boolean colourComplete, List<Exercise> today,
            int modulesDone) {
    }

    public static Plan plan(List<Exercise> exercises, long sessionsDone) {
        List<List<Exercise>> modules = modules(exercises);
        int count = modules.size();
        int modulesDone = (int) Math.min(count, sessionsDone / SESSIONS_PER_MODULE);
        boolean complete = modulesDone == count;
        int module = Math.min(count, modulesDone + 1);
        int session = (int) (sessionsDone - (long) (module - 1) * SESSIONS_PER_MODULE);
        List<Exercise> list = modules.get(module - 1);
        List<Exercise> today = new ArrayList<>();
        int take = Math.min(EXERCISES_PER_SESSION, list.size());
        for (int i = 0; i < take; i++) {
            today.add(list.get((session * EXERCISES_PER_SESSION + i) % list.size()));
        }
        return new Plan(module, count, Math.min(SESSIONS_PER_MODULE, session + 1), complete, today, modulesDone);
    }

    /**
     * The modules of a colour. With Pat's module points (Green), module n holds the exercises assessed at its end;
     * without them (Yellow), the exercises in groups of three, in order.
     */
    public static List<List<Exercise>> modules(List<Exercise> exercises) {
        List<Exercise> sorted = exercises.stream().sorted(Comparator.comparingInt(Exercise::getNumber)).toList();
        int last = sorted.stream().flatMap(e -> e.getPoints().stream()).filter(ExercisePoints::isModule)
                .mapToInt(p -> Integer.parseInt(p.substring(p.indexOf('_') + 1))).max().orElse(0);
        List<List<Exercise>> modules = new ArrayList<>();
        if (last > 0) {
            for (int m = 1; m <= last; m++) {
                String point = ExercisePoints.module(m);
                List<Exercise> in = sorted.stream().filter(e -> e.getPoints().contains(point)).toList();
                if (!in.isEmpty()) {
                    modules.add(in);
                }
            }
        } else {
            for (int i = 0; i < sorted.size(); i += GROUP_SIZE) {
                modules.add(sorted.subList(i, Math.min(sorted.size(), i + GROUP_SIZE)));
            }
        }
        return modules;
    }

    public record Review(String point, List<Exercise> exercises) {
    }

    /**
     * The next ratings due after finishing a module (its exercises at the end of that module) or the colour (every
     * exercise at the end), skipping any already rated. {@code rated} holds "exerciseId:point".
     */
    public static Review reviewDue(List<Exercise> exercises, Plan plan, Set<String> rated) {
        List<List<Exercise>> modules = modules(exercises);
        for (int m = 1; m <= plan.modulesDone(); m++) {
            String point = ExercisePoints.module(m);
            List<Exercise> due = modules.get(m - 1).stream()
                    .filter(e -> e.getPoints().contains(point) && !rated.contains(e.getId() + ":" + point)).toList();
            if (!due.isEmpty()) {
                return new Review(point, due);
            }
        }
        if (plan.colourComplete()) {
            List<Exercise> due = exercises.stream()
                    .filter(e -> e.getPoints().contains(ExercisePoints.END)
                            && !rated.contains(e.getId() + ":" + ExercisePoints.END))
                    .sorted(Comparator.comparingInt(Exercise::getNumber)).toList();
            if (!due.isEmpty()) {
                return new Review(ExercisePoints.END, due);
            }
        }
        return null;
    }
}
