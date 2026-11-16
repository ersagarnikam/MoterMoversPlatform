package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentRefund;
import uk.co.motormovers.platform.school.model.School;

/** The tax included in a tax-inclusive amount: amount x rate / (1 + rate), to the minor unit (go-live task 25, part C). */
class PaymentTaxTest {

    private final School school = new School("Test Primary", null, Region.UK);

    @Test
    void vatAt20PercentIsASixthOfTheAmount() {
        assertThat(tax(12_000, 2_000)).isEqualTo(2_000);
        assertThat(tax(45_000, 2_000)).isEqualTo(7_500);
    }

    @Test
    void roundsToTheNearestMinorUnit() {
        // 999 / 6 = 166.5, half up.
        assertThat(tax(999, 2_000)).isEqualTo(167);
        // VAT at 23%: 5000 x 0.23 / 1.23 = 934.96.
        assertThat(tax(5_000, 2_300)).isEqualTo(935);
        // GST at 10%: 4500 x 0.1 / 1.1 = 409.09.
        assertThat(tax(4_500, 1_000)).isEqualTo(409);
        // A part percent: 10000 x 0.135 / 1.135 = 1189.43.
        assertThat(tax(10_000, 1_350)).isEqualTo(1_189);
        assertThat(tax(1, 2_000)).isEqualTo(0);
    }

    @Test
    void taxOnPartRefundsNeverGoesNegativeAndAddsUpToTheTaxOnThePayment() {
        Payment payment = payment(11);
        payment.includesTaxAt(2_000);
        List<Integer> taxes = new ArrayList<>();
        for (int part : List.of(3, 3, 3, 2)) {
            Integer tax = payment.taxInRefund(part);
            taxes.add(tax);
            payment.getRefunds().add(new PaymentRefund(payment, part, tax, "Test", LocalDate.of(2026, 11, 6), "test",
                    Instant.parse("2026-11-06T09:00:00Z"), null, null, taxes.size()));
        }
        assertThat(taxes).allMatch(t -> t >= 0);
        assertThat(taxes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(payment.taxPence());
    }

    @Test
    void noRateMeansNoTaxLine() {
        assertThat(payment(4_500).taxPence()).isNull();
    }

    @Test
    void aRateOfNoughtIncludesNoTax() {
        assertThat(tax(4_500, 0)).isEqualTo(0);
    }

    @Test
    void theRateInBasisPointsComesFromTheCountrysPercent() {
        assertThat(country("20").taxRateBp()).isEqualTo(2_000);
        assertThat(country("23").taxRateBp()).isEqualTo(2_300);
        assertThat(country("13.5").taxRateBp()).isEqualTo(1_350);
    }

    private Integer tax(int amountPence, int rateBp) {
        Payment payment = payment(amountPence);
        payment.includesTaxAt(rateBp);
        return payment.taxPence();
    }

    private Payment payment(int amountPence) {
        return Payment.bySchool(school, PaymentPurpose.LICENCE, "Licence", amountPence, LocalDate.of(2026, 11, 6), null,
                "team@example.test", Instant.parse("2026-11-06T09:00:00Z"));
    }

    private static CountrySettings country(String percent) {
        return new CountrySettings("Test", "GBP", "en-GB", "VAT", new BigDecimal(percent), "Europe/London",
                new CountrySettings.SchoolYear(9, 8, 3, 9, 1, 5), List.of("Year 1"), null, "Credit note", "UK time", "the UK", "This half-term", null);
    }
}
