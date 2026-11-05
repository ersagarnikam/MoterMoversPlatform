package uk.co.motormovers.platform.home.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * The rules of Motor Movers at Home, from Pat's answers of 2026-11-03 and 2026-11-05 (docs/home-programme-design.md). Children move
 * through the colours at their own pace, so the colour does not follow the age: young children start at Orange and
 * older ones at Green, the core programme, then go on through the colours. A rhyme colour (Red, Orange, Yellow) does
 * one rhyme a week, five times, in the order of its grid; a colour with modules (Green to Violet) builds each module up
 * over five weeks, adding one or two exercises a week. Pat confirmed five weeks for Green to Indigo; Violet is assumed
 * the same until she says otherwise.
 */
public final class HomeRules {

    /** Babies and toddlers under 2 start at Red, from 2 at Orange (Pat confirmed, 2026-11-05). */
    static final int ORANGE_FROM_MONTHS = 24;

    /** From the age children start school in the UK (Pat's "4/5"), they start at Green, the core programme. */
    static final int GREEN_FROM_MONTHS = 48;

    /** The check asks about movements from the year before the child's age up to their age. */
    static final int CHECK_LOOKBACK_MONTHS = 12;

    public static final int SESSIONS_PER_WEEK = 5;

    /** Five weeks of five sessions a module: confirmed for Green to Indigo (2026-11-05). */
    public static final int WEEKS_PER_MODULE = 5;

    /** New exercises in a module's first week; the rest are added one or two a week after that. */
    static final int NEW_IN_FIRST_WEEK = 2;

    private HomeRules() {
    }

    /** The colour a child starts at: Red for babies and toddlers, Orange for nursery age, Green from school age. */
    public static ColourStage startingColour(int ageMonths) {
        if (ageMonths >= GREEN_FROM_MONTHS) {
            return ColourStage.GREEN;
        }
        return ageMonths >= ORANGE_FROM_MONTHS ? ColourStage.ORANGE : ColourStage.RED;
    }

    /** The movements the starting check asks about: those a child usually does in the year up to their age. */
    public static boolean askAbout(Milestone m, int ageMonths) {
        return m.getAgeFromMonths() <= ageMonths && m.getAgeToMonths() >= ageMonths - CHECK_LOOKBACK_MONTHS;
    }

    /**
     * The check (Pat's developmental charts) is done at the start, middle and end of each school year, as in school:
     * it is due when it has not been done yet in the current part of the year.
     */
    public static boolean checkDue(LocalDate checkedOn, LocalDate today) {
        return checkedOn == null || YearPoint.schoolYear(checkedOn) != YearPoint.schoolYear(today)
                || YearPoint.on(checkedOn) != YearPoint.on(today);
    }

    /** A colour with module points in Pat's grid (Green to Violet); the rhyme colours have only a start and an end. */
    public static boolean hasModules(List<Exercise> exercises) {
        return exercises.stream().flatMap(e -> e.getPoints().stream()).anyMatch(ExercisePoints::isModule);
    }

    /**
     * Where a child is in a colour. {@code module} is the module, or the rhyme for a rhyme colour; {@code week} the
     * week within a module (always 1 for a rhyme). {@code today} is the session; {@code newThisWeek} the exercises it
     * adds this week.
     */
    public record Plan(boolean rhymes, int module, int modules, int week, int weeks, int sessionInModule,
            int sessionsPerModule, boolean colourComplete, List<Exercise> today, List<Exercise> newThisWeek,
            int modulesDone) {
    }

    public static Plan plan(List<Exercise> exercises, long sessionsDone) {
        boolean rhymes = !hasModules(exercises);
        List<List<Exercise>> modules = modules(exercises);
        int count = modules.size();
        int perModule = rhymes ? SESSIONS_PER_WEEK : SESSIONS_PER_WEEK * WEEKS_PER_MODULE;
        int modulesDone = (int) Math.min(count, sessionsDone / perModule);
        boolean complete = modulesDone == count;
        int module = Math.min(count, modulesDone + 1);
        int session = (int) Math.min(perModule - 1, sessionsDone - (long) (module - 1) * perModule);
        int weeks = rhymes ? 1 : WEEKS_PER_MODULE;
        int week = rhymes ? 1 : session / SESSIONS_PER_WEEK + 1;
        List<Exercise> list = modules.get(module - 1);
        List<Exercise> today;
        List<Exercise> added;
        if (rhymes) {
            today = list;
            added = List.of();
        } else {
            List<List<Exercise>> byWeek = buildUp(list, module == 1 ? List.of() : modules.get(module - 2));
            today = byWeek.subList(0, week).stream().flatMap(List::stream).toList();
            added = week == 1 ? List.of() : byWeek.get(week - 1);
        }
        return new Plan(rhymes, module, count, week, weeks, session + 1, perModule, complete, today, added,
                modulesDone);
    }

    /**
     * A module's exercises week by week: those carried on from the module before from the first week, with the first
     * two new ones; the other new ones are added one or two a week, in Pat's order, so all are in by the last week.
     */
    static List<List<Exercise>> buildUp(List<Exercise> module, List<Exercise> previous) {
        List<Exercise> carried = module.stream().filter(previous::contains).toList();
        List<Exercise> fresh = module.stream().filter(e -> !previous.contains(e)).toList();
        List<List<Exercise>> weeks = new ArrayList<>();
        int first = Math.min(NEW_IN_FIRST_WEEK, fresh.size());
        List<Exercise> week1 = new ArrayList<>(carried);
        week1.addAll(fresh.subList(0, first));
        weeks.add(week1);
        int rest = fresh.size() - first;
        int laterWeeks = WEEKS_PER_MODULE - 1;
        int from = first;
        for (int w = 0; w < laterWeeks; w++) {
            int take = rest / laterWeeks + (w < rest % laterWeeks ? 1 : 0);
            weeks.add(fresh.subList(from, from + take));
            from += take;
        }
        return weeks;
    }

    /**
     * The modules of a colour. With Pat's module points (Green to Violet), module n holds the exercises assessed at
     * its end; without them (the rhyme colours), each rhyme on its own, in order: one a week.
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
            sorted.forEach(e -> modules.add(List.of(e)));
        }
        return modules;
    }

    /** "Module 2 of 6 · week 3 of 5", "Rhyme 4 of 18 · session 2 of 5", or the colour done. */
    public static String progressLabel(Plan plan) {
        String unit = plan.rhymes() ? "rhymes" : "modules";
        if (plan.colourComplete()) {
            return "All " + plan.modules() + " " + unit + " done: keep practising";
        }
        return plan.rhymes()
                ? "Rhyme " + plan.module() + " of " + plan.modules() + " · session " + plan.sessionInModule() + " of "
                        + plan.sessionsPerModule()
                : "Module " + plan.module() + " of " + plan.modules() + " · week " + plan.week() + " of "
                        + plan.weeks();
    }

    /** "Module 2 · week 3", "Rhyme 4 · session 2". */
    public static String sessionTitle(Plan plan) {
        return plan.rhymes()
                ? "Rhyme " + plan.module() + " · session " + plan.sessionInModule()
                : "Module " + plan.module() + " · week " + plan.week();
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
