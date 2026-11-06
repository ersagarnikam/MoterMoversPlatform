package uk.co.motormovers.platform.assessment.util;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.common.config.CountrySettings;

/**
 * The deployment's school year, from its {@link CountrySettings}: which school year a date is in, and which of the
 * three assessment points. The UK's runs from September, Ireland's from September with an earlier end point, and
 * Australia's is the calendar year, so a year there is never split.
 */
@Component
public class SchoolYear {

    private final int firstMonth;
    private final int startMonth;
    private final int middleMonth;
    private final int endMonth;

    @Autowired
    public SchoolYear(CountrySettings country) {
        this(country.schoolYear().firstMonth(), country.schoolYear().startMonth(), country.schoolYear().middleMonth(),
                country.schoolYear().endMonth());
    }

    /** Months 1 (January) to 12: when the year begins, and when each assessment point starts. */
    public SchoolYear(int firstMonth, int startMonth, int middleMonth, int endMonth) {
        this.firstMonth = firstMonth;
        this.startMonth = startMonth;
        this.middleMonth = middleMonth;
        this.endMonth = endMonth;
    }

    /** The assessment point a date falls in: each runs until the next starts, the end point until the new year. */
    public YearPoint pointOn(LocalDate date) {
        int month = monthsAfterStart(date.getMonthValue());
        if (month >= monthsAfterStart(endMonth)) {
            return YearPoint.END;
        }
        return month >= monthsAfterStart(middleMonth) ? YearPoint.MIDDLE : YearPoint.START;
    }

    /**
     * The calendar year the school year began in: 2026 for September 2026 to August 2027 in the UK, and 2027 for all
     * of 2027 in Australia.
     */
    public int yearOf(LocalDate date) {
        return date.getMonthValue() >= firstMonth ? date.getYear() : date.getYear() - 1;
    }

    private int monthsAfterStart(int month) {
        return Math.floorMod(month - startMonth, 12);
    }
}
