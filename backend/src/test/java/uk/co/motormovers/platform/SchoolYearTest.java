package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.util.SchoolYear;

/** The school year and its assessment points in each country (go-live task 25, part D). */
class SchoolYearTest {

    private static final SchoolYear UK = new SchoolYear(9, 9, 1, 5);

    private static final SchoolYear IRELAND = new SchoolYear(9, 9, 1, 4);

    private static final SchoolYear AUSTRALIA = new SchoolYear(1, 1, 5, 9);

    @Test
    void theUkIsAsBefore() {
        assertThat(UK.pointOn(day(2026, 9, 1))).isEqualTo(YearPoint.START);
        assertThat(UK.pointOn(day(2026, 12, 31))).isEqualTo(YearPoint.START);
        assertThat(UK.pointOn(day(2027, 1, 1))).isEqualTo(YearPoint.MIDDLE);
        assertThat(UK.pointOn(day(2027, 4, 30))).isEqualTo(YearPoint.MIDDLE);
        assertThat(UK.pointOn(day(2027, 5, 1))).isEqualTo(YearPoint.END);
        assertThat(UK.pointOn(day(2027, 8, 31))).isEqualTo(YearPoint.END);
        assertThat(UK.yearOf(day(2026, 9, 1))).isEqualTo(2026);
        assertThat(UK.yearOf(day(2027, 8, 31))).isEqualTo(2026);
        assertThat(UK.yearOf(day(2027, 9, 1))).isEqualTo(2027);
    }

    @Test
    void irelandEndsInJune() {
        assertThat(IRELAND.pointOn(day(2026, 11, 2))).isEqualTo(YearPoint.START);
        assertThat(IRELAND.pointOn(day(2027, 3, 31))).isEqualTo(YearPoint.MIDDLE);
        assertThat(IRELAND.pointOn(day(2027, 4, 1))).isEqualTo(YearPoint.END);
        assertThat(IRELAND.pointOn(day(2027, 6, 30))).isEqualTo(YearPoint.END);
        // The summer holidays still count as the end of the year just gone.
        assertThat(IRELAND.pointOn(day(2027, 8, 20))).isEqualTo(YearPoint.END);
        assertThat(IRELAND.yearOf(day(2027, 8, 20))).isEqualTo(2026);
    }

    @Test
    void australiaIsTheCalendarYearAndIsNeverSplit() {
        assertThat(AUSTRALIA.pointOn(day(2027, 1, 27))).isEqualTo(YearPoint.START);
        assertThat(AUSTRALIA.pointOn(day(2027, 4, 30))).isEqualTo(YearPoint.START);
        assertThat(AUSTRALIA.pointOn(day(2027, 5, 1))).isEqualTo(YearPoint.MIDDLE);
        assertThat(AUSTRALIA.pointOn(day(2027, 9, 1))).isEqualTo(YearPoint.END);
        assertThat(AUSTRALIA.pointOn(day(2027, 12, 31))).isEqualTo(YearPoint.END);
        assertThat(AUSTRALIA.yearOf(day(2027, 1, 1))).isEqualTo(2027);
        assertThat(AUSTRALIA.yearOf(day(2027, 12, 31))).isEqualTo(2027);
    }

    private static LocalDate day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth);
    }
}
