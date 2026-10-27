package uk.co.motormovers.platform.payment.stripe;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Stripe's API (https://docs.stripe.com/api/checkout/sessions/create), form-encoded with the secret key. */
public class StripeApiClient implements StripeClient {

    private record Session(String id, String url) {
    }

    private final RestClient api;

    public StripeApiClient(RestClient.Builder builder, String secretKey) {
        this.api = builder.baseUrl("https://api.stripe.com/v1")
                .defaultHeader("Authorization", "Bearer " + secretKey.strip())
                .build();
    }

    @Override
    public Checkout createCheckout(Long paymentId, int amountPence, String description, String payerEmail,
            String successUrl, String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", "gbp");
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(amountPence));
        form.add("line_items[0][price_data][product_data][name]", description);
        form.add("client_reference_id", String.valueOf(paymentId));
        form.add("metadata[paymentId]", String.valueOf(paymentId));
        form.add("payment_intent_data[metadata][paymentId]", String.valueOf(paymentId));
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        if (payerEmail != null) {
            form.add("customer_email", payerEmail);
        }
        try {
            Session session = api.post().uri("/checkout/sessions").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Session.class);
            if (session == null || session.url() == null) {
                throw new IllegalStateException("Stripe did not open a payment page. Please try again.");
            }
            return new Checkout(session.id(), session.url());
        } catch (RestClientResponseException e) {
            // Stripe's message can name the account's settings; the payer only needs to know it did not work.
            throw new IllegalStateException("Card payments are not available just now. Please try again later.", e);
        }
    }
}
