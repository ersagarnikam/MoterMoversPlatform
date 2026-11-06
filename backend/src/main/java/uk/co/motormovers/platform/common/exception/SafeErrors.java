package uk.co.motormovers.platform.common.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Describes an error for the log without its message. Messages hold what the error was about: a mail server's quotes
 * the address, a database's the rejected values, an outside service's its token. Stack traces repeat them, so these
 * errors are logged by type and place alone.
 */
public final class SafeErrors {

    private static final String OURS = "uk.co.motormovers.";
    private static final int MOST_CAUSES = 6;
    private static final int MOST_FRAMES = 3;

    private SafeErrors() {
    }

    /** For example "java.lang.IllegalStateException at Foo.bar(Foo.java:12) <- java.io.IOException". */
    public static String describe(Throwable error) {
        List<String> chain = new ArrayList<>();
        for (Throwable e = error; e != null && chain.size() < MOST_CAUSES; e = e.getCause()) {
            String frames = String.join(", ", frames(e));
            chain.add(e.getClass().getName() + (frames.isEmpty() ? "" : " at " + frames));
        }
        return String.join(" <- ", chain);
    }

    /** The first frames in our own code, or failing that the very first. */
    private static List<String> frames(Throwable e) {
        List<String> ours = Arrays.stream(e.getStackTrace())
                .filter(f -> f.getClassName().startsWith(OURS))
                .limit(MOST_FRAMES)
                .map(StackTraceElement::toString)
                .toList();
        return ours.isEmpty() ? Arrays.stream(e.getStackTrace()).limit(1).map(StackTraceElement::toString).toList()
                : ours;
    }
}
