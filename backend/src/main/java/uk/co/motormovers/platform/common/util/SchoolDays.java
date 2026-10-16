package uk.co.motormovers.platform.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Weekday arithmetic. Term dates and bank holidays are not modelled yet. */
public final class SchoolDays {

    private SchoolDays() {
    }

    public static boolean isSchoolDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** The last {@code count} school days up to and including {@code end}, oldest first. */
    public static List<LocalDate> lastSchoolDays(LocalDate end, int count) {
        List<LocalDate> days = new ArrayList<>(count);
        LocalDate date = end;
        while (days.size() < count) {
            if (isSchoolDay(date)) {
                days.add(date);
            }
            date = date.minusDays(1);
        }
        Collections.reverse(days);
        return days;
    }
}
