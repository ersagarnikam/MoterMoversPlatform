package uk.co.motormovers.platform.common.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.springframework.mail.MailSendException;

import jakarta.mail.MessagingException;

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

    /** The mail server's reply code, the one part of its refusal safe to log: its text quotes the address. */
    public static String smtpCode(Throwable error) {
        List<Throwable> found = new ArrayList<>();
        collect(error, found, 0);
        return found.stream()
                .map(SafeErrors::returnCode)
                .filter(code -> code > 0)
                .findFirst()
                .map(String::valueOf)
                .orElse("none");
    }

    private static void collect(Throwable e, List<Throwable> found, int depth) {
        if (e == null || depth > MOST_CAUSES) {
            return;
        }
        found.add(e);
        collect(e.getCause(), found, depth + 1);
        if (e instanceof MessagingException m) {
            collect(m.getNextException(), found, depth + 1);
        }
        if (e instanceof MailSendException m) {
            for (Exception each : m.getMessageExceptions()) {
                collect(each, found, depth + 1);
            }
        }
    }

    private static int returnCode(Throwable e) {
        return switch (e) {
            case SMTPAddressFailedException f -> f.getReturnCode();
            case SMTPSendFailedException f -> f.getReturnCode();
            case SMTPSenderFailedException f -> f.getReturnCode();
            default -> 0;
        };
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
