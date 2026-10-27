package uk.co.motormovers.platform.assessment.model;

import java.time.LocalDate;
import java.time.Month;

/**
 * When in the school year the developmental assessment is done: the start, middle or end. The school year runs
 * from September to August.
 */
public enum YearPoint {
    START("Start of year"),
    MIDDLE("Middle of year"),
    END("End of year");

    private final String label;

    YearPoint(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** September to December is the start, January to April the middle, May to August the end. */
    public static YearPoint on(LocalDate date) {
        int m = date.getMonthValue();
        if (m >= Month.SEPTEMBER.getValue()) {
            return START;
        }
        return m <= Month.APRIL.getValue() ? MIDDLE : END;
    }

    /** The calendar year the school year began in: 2026 for September 2026 to August 2027. */
    public static int schoolYear(LocalDate date) {
        return date.getMonthValue() >= Month.SEPTEMBER.getValue() ? date.getYear() : date.getYear() - 1;
    }
}
