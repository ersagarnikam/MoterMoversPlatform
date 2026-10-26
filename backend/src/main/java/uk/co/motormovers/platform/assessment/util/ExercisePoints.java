package uk.co.motormovers.platform.assessment.util;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * The points an exercise is assessed at, as stored: {@code START} (start of the colour), {@code MODULE_3} (end of
 * module 3) and {@code END} (end of the colour, Pat's "Final end of Green").
 */
public final class ExercisePoints {

    public static final String START = "START";
    public static final String END = "END";

    private static final Pattern MODULE = Pattern.compile("MODULE_([1-9][0-9]?)");

    /** Start first, then the modules in order, then the end of the colour. */
    public static final Comparator<String> ORDER = Comparator.comparingInt(ExercisePoints::order);

    private ExercisePoints() {
    }

    public static boolean valid(String point) {
        return START.equals(point) || END.equals(point) || (point != null && MODULE.matcher(point).matches());
    }

    public static String module(int module) {
        return "MODULE_" + module;
    }

    public static boolean isModule(String point) {
        return point != null && MODULE.matcher(point).matches();
    }

    static int order(String point) {
        if (START.equals(point)) {
            return 0;
        }
        if (END.equals(point)) {
            return 1000;
        }
        Matcher m = MODULE.matcher(point);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unknown assessment point " + point);
        }
        return Integer.parseInt(m.group(1));
    }

    /** "Start", "End of module 3", "End of Green". */
    public static String label(String point, ColourStage stage) {
        if (START.equals(point)) {
            return "Start";
        }
        if (END.equals(point)) {
            return "End of " + stage.label();
        }
        return "End of module " + order(point);
    }
}
