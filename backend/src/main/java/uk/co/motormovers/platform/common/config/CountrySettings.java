package uk.co.motormovers.platform.common.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;

/**
 * What differs between the countries a deployment can serve: money, tax, the time zone, the school year and its year
 * names, and the legal entity that contracts with schools and families. One block per region in application.yml
 * ({@code platform.countries}); {@code PLATFORM_REGION} picks the deployment's, and every value can be overridden by
 * an environment variable (for example {@code PLATFORM_COUNTRIES_IE_TAX_PERCENT}). Values still to be confirmed by Pat
 * or Kay are marked in application.yml.
 */
public record CountrySettings(
        String name,
        String currency,
        String locale,
        String taxLabel,
        BigDecimal taxPercent,
        String timeZone,
        SchoolYear schoolYear,
        List<String> yearNames,
        LegalEntity legalEntity) {

    /**
     * The school year runs from {@code firstMonth} to {@code lastMonth} (1 = January). Its three assessment points start
     * in {@code startMonth}, {@code middleMonth} and {@code endMonth}; each runs until the next one starts, the end
     * point until the next school year.
     */
    public record SchoolYear(int firstMonth, int lastMonth, int terms, int startMonth, int middleMonth,
            int endMonth) {
    }

    /** Who contracts with schools and families in this country. Placeholders until Kay decides (go-live decision 1). */
    public record LegalEntity(String name, String address, String companyNumber, String taxNumber, String email,
            String phone) {
    }

    /** Fails start-up with a clear message when a value cannot work, rather than later on a payment or a date. */
    public CountrySettings validated(String region) {
        String where = "platform.countries." + region + ".";
        require(name != null && !name.isBlank(), where + "name is missing");
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(where + "currency is not an ISO 4217 code: " + currency);
        }
        try {
            new Locale.Builder().setLanguageTag(locale).build();
        } catch (IllformedLocaleException | NullPointerException e) {
            throw new IllegalStateException(where + "locale is not a language tag: " + locale);
        }
        require(taxLabel != null && !taxLabel.isBlank(), where + "tax-label is missing");
        require(taxPercent != null && taxPercent.signum() >= 0 && taxPercent.compareTo(BigDecimal.valueOf(100)) < 0,
                where + "tax-percent must be from 0 to under 100");
        try {
            ZoneId.of(timeZone);
        } catch (RuntimeException e) {
            throw new IllegalStateException(where + "time-zone is not a time zone: " + timeZone);
        }
        require(schoolYear != null, where + "school-year is missing");
        for (int month : List.of(schoolYear.firstMonth(), schoolYear.lastMonth(), schoolYear.startMonth(),
                schoolYear.middleMonth(), schoolYear.endMonth())) {
            require(month >= 1 && month <= 12, where + "school-year months must be from 1 to 12");
        }
        require(schoolYear.startMonth() == schoolYear.firstMonth(),
                where + "school-year.start-month must be the school year's first month");
        require(schoolYear.terms() > 0, where + "school-year.terms must be at least 1");
        require(yearNames != null && !yearNames.isEmpty(), where + "year-names is missing");
        require(legalEntity != null, where + "legal-entity is missing");
        return this;
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
