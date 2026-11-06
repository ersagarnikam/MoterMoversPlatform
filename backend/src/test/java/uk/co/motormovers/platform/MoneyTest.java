package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.common.util.Money;

/** Amounts in each deployment's currency, as in emails and phone notifications (go-live task 25, part B). */
class MoneyTest {

    @Test
    void poundsInTheUk() {
        Money uk = new Money("GBP", "en-GB");
        assertThat(uk.format(4_500)).isEqualTo("£45");
        assertThat(uk.format(4_550)).isEqualTo("£45.50");
        assertThat(uk.format(175_000)).isEqualTo("£1,750");
        assertThat(uk.format(0)).isEqualTo("£0");
        assertThat(uk.stripeCurrency()).isEqualTo("gbp");
    }

    @Test
    void eurosInIreland() {
        Money ie = new Money("EUR", "en-IE");
        assertThat(ie.format(4_500)).isEqualTo("€45");
        assertThat(ie.format(148_750)).isEqualTo("€1,487.50");
        assertThat(ie.stripeCurrency()).isEqualTo("eur");
    }

    @Test
    void dollarsInAustralia() {
        Money au = new Money("AUD", "en-AU");
        assertThat(au.format(4_500)).isEqualTo("$45");
        assertThat(au.format(3_505)).isEqualTo("$35.05");
        assertThat(au.stripeCurrency()).isEqualTo("aud");
    }
}
