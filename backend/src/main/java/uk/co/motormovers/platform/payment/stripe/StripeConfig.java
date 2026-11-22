package uk.co.motormovers.platform.payment.stripe;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import uk.co.motormovers.platform.common.util.Money;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    /** The stand-in for Stripe (STRIPE_FAKE=true): development, demos and the browser tests only. */
    @Bean
    @ConditionalOnProperty(name = "platform.stripe.fake", havingValue = "true")
    FakeStripe fakeStripe(JsonMapper json, Money money, Clock clock) {
        log.warn("STRIPE_FAKE is on: card payments are pretended on this server. Never in production.");
        return new FakeStripe(json, money.stripeCurrency(), clock);
    }

    /**
     * The real client with both Stripe keys set; the stand-in when that is switched on; otherwise one that refuses,
     * since Pay by card is hidden.
     */
    @Bean
    @Primary
    StripeClient stripeClient(StripeProperties properties, Money money, ObjectProvider<FakeStripe> fake) {
        FakeStripe standIn = fake.getIfAvailable();
        if (standIn != null) {
            return standIn;
        }
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
