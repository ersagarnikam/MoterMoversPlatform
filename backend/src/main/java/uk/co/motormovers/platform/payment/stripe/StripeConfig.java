package uk.co.motormovers.platform.payment.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import uk.co.motormovers.platform.common.util.Money;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    /** The real client with both Stripe keys set; otherwise one that refuses, since Pay by card is hidden. */
    @Bean
    StripeClient stripeClient(StripeProperties properties, Money money) {
        if (properties.enabled()) {
            return new StripeApiClient(RestClient.builder(), properties.secretKey(), money.stripeCurrency());
        }
        log.info("Card payments are off: payments are marked paid by hand until the platform.stripe keys are set");
        return new StripeClient() {
            @Override
            public Checkout createCheckout(Long paymentId, int amountPence, String description, String payerEmail,
                    String successUrl, String cancelUrl) {
                throw new IllegalStateException("Card payments are not switched on");
            }

            @Override
            public Refund createRefund(String paymentIntent, int amountMinor, Long paymentId, String idempotencyKey) {
                throw new IllegalStateException("Card payments are not switched on, so Stripe cannot make this refund");
            }
        };
    }
}
