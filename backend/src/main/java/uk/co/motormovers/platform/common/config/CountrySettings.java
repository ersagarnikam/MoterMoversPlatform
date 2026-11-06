package uk.co.motormovers.platform.common.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Currency;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
        LegalEntity legalEntity,
        String creditNoteTitle,
        String timeLabel) {

    /**
     * The school year runs from {@code firstMonth} to {@code lastMonth} (1 = January). Its three assessment points start
     * in {@code startMonth}, {@code middleMonth} and {@code endMonth}; each runs until the next one starts, the end
     * point until the next school year.
     */
    public record SchoolYear(int firstMonth, int lastMonth, int terms, int startMonth, int middleMonth,
            int endMonth) {
    }

    /**
     * Who contracts with schools and families in this country. Placeholders until Kay decides (go-live decision 1).
     * {@code taxNumberLabel} is "VAT number" or "ABN". A value may be left blank if it does not apply, but a production
     * deployment refuses to start with a placeholder (ProductionChecks).
     */
    public record LegalEntity(String name, String address, String companyNumber, String taxNumberLabel,
            String taxNumber, String email, String phone) {

        public static final String PLACEHOLDER = "To be confirmed";

        public boolean isPlaceholder() {
            return name == null || name.isBlank()
                    || Stream.of(name, address, companyNumber, taxNumber, email, phone).anyMatch(PLACEHOLDER::equals);
        }

        /**
         * One line for footers, invoices and the certificate: "Neuroway Ltd, 1 High Street. Company number 123. VAT
         * number GB456." Null while the entity is a placeholder, so nothing untrue is shown.
         */
        public String line() {
            if (isPlaceholder()) {
                return null;
            }
            StringBuilder line = new StringBuilder(name);
            if (present(address)) {
                line.append(", ").append(address);
            }
            line.append('.');
            if (present(companyNumber)) {
                line.append(" Company number ").append(companyNumber).append('.');
            }
            if (present(taxNumber)) {
                line.append(' ').append(taxNumberLabel).append(' ').append(taxNumber).append('.');
            }
            return line.toString();
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** Fails start-up with a clear message when a value cannot work, rather than later on a payment or a date. */
    public CountrySettings validated(String region) {
        String where = "platform.countries." + region + ".";
        require(name != null && !name.isBlank(), where + "name is missing");
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CountrySettingsException(where + "currency is not an ISO 4217 code: " + currency);
        }
        try {
            new Locale.Builder().setLanguageTag(locale).build();
        } catch (IllformedLocaleException | NullPointerException e) {
            throw new CountrySettingsException(where + "locale is not a language tag: " + locale);
        }
        require(taxLabel != null && !taxLabel.isBlank(), where + "tax-label is missing");
        require(taxPercent != null && taxPercent.signum() >= 0 && taxPercent.compareTo(BigDecimal.valueOf(100)) < 0,
                where + "tax-percent must be from 0 to under 100");
        try {
            ZoneId.of(timeZone);
        } catch (RuntimeException e) {
            throw new CountrySettingsException(where + "time-zone is not a time zone: " + timeZone);
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
        require(creditNoteTitle != null && !creditNoteTitle.isBlank(), where + "credit-note-title is missing");
        require(timeLabel != null && !timeLabel.isBlank(), where + "time-label is missing");
        return this;
    }

    /** The tax rate in basis points: 2000 for 20%, 1350 for 13.5%. */
    public int taxRateBp() {
        return taxPercent.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new CountrySettingsException(message);
        }
    }
}
