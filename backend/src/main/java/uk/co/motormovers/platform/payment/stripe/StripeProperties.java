package uk.co.motormovers.platform.payment.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paying by card online with Stripe Checkout (see deploy/RUNBOOK.md, Card payments). {@code secretKey} is the Stripe
 * account's secret key (STRIPE_SECRET_KEY; sk_test_... for testing), {@code webhookSecret} the signing secret of the
 * webhook endpoint that tells the platform a payment went through (STRIPE_WEBHOOK_SECRET, whsec_...). Without both,
 * Pay by card is hidden and payments are marked paid by hand as before.
 */
@ConfigurationProperties(prefix = "platform.stripe")
public record StripeProperties(String secretKey, String webhookSecret) {

    public boolean enabled() {
        return present(secretKey) && present(webhookSecret);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
