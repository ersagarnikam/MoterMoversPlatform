package uk.co.motormovers.platform.payment.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    /** The real client with both Stripe keys set; otherwise one that refuses, since Pay by card is hidden. */
    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        if (properties.enabled()) {
            return new StripeApiClient(RestClient.builder(), properties.secretKey());
        }
        log.info("Card payments are off: payments are marked paid by hand until the platform.stripe keys are set");
        return (paymentId, amountPence, description, payerEmail, successUrl, cancelUrl) -> {
            throw new IllegalStateException("Card payments are not switched on");
        };
    }
}
