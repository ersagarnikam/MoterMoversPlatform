package uk.co.motormovers.platform.common.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.common.config.CountrySettings;

/**
 * Amounts as people read them, in the deployment's currency and locale (its {@link CountrySettings}), as on the web:
 * "£45", "€45.50", "$1,750". Amounts are held in the currency's minor unit (pence or cents; the {@code *Pence} fields
 * and columns keep their names).
 */
@Component
public class Money {

    private final Currency currency;
    private final Locale locale;

    @Autowired
    public Money(CountrySettings country) {
        this(country.currency(), country.locale());
    }

    /** For a given currency and locale, e.g. ("EUR", "en-IE"). */
    public Money(String currencyCode, String languageTag) {
        this.currency = Currency.getInstance(currencyCode);
        this.locale = Locale.forLanguageTag(languageTag);
    }

    /** 4500 -> "£45", 4550 -> "£45.50": the minor unit shown only when there is some. */
    public String format(long minor) {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        BigDecimal amount = BigDecimal.valueOf(minor).movePointLeft(digits);
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        format.setMinimumFractionDigits(whole ? 0 : digits);
        format.setMaximumFractionDigits(whole ? 0 : digits);
        return format.format(amount);
    }

    /** The ISO 4217 code in lower case, as Stripe wants it ("gbp", "eur", "aud"). */
    public String stripeCurrency() {
        return currency.getCurrencyCode().toLowerCase(Locale.ROOT);
    }
}
