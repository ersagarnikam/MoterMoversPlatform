package uk.co.motormovers.platform.payment.stripe;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Stripe's API, form-encoded with the secret key: Checkout sessions (https://docs.stripe.com/api/checkout/sessions/create),
 * refunds, and for subscriptions the customer portal, cancelling and an invoice's payments. The subscription calls were
 * checked against Stripe's documentation on 2026-11-21.
 */
public class StripeApiClient implements StripeClient {

    private record Session(String id, String url) {
    }

    private record Portal(String url) {
    }

    /** One page of https://docs.stripe.com/api/invoice-payment/list. */
    private record InvoicePayments(List<InvoicePayment> data) {
    }

    private record InvoicePayment(PaidWith payment) {
    }

    private record PaidWith(String type, String payment_intent) {
    }

    private final RestClient api;
    private final String currency;

    /** {@code currency}: the deployment's, in lower case as Stripe wants it ("gbp", "eur", "aud"). */
    public StripeApiClient(RestClient.Builder builder, String secretKey, String currency) {
        this.currency = currency;
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
        form.add("line_items[0][price_data][currency]", currency);
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

    @Override
    public Checkout createSubscriptionCheckout(Long subscriptionRowId, Long familyId, Long payerId, int monthlyMinor,
            String description, String payerEmail, String successUrl, String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(monthlyMinor));
        form.add("line_items[0][price_data][recurring][interval]", "month");
        form.add("line_items[0][price_data][product_data][name]", description);
        form.add("client_reference_id", "home-subscription-" + subscriptionRowId);
        // On the session and on the subscription: Stripe copies the subscription's onto each invoice.
        for (String on : List.of("metadata", "subscription_data[metadata]")) {
            form.add(on + "[" + SUBSCRIPTION_ROW + "]", String.valueOf(subscriptionRowId));
            form.add(on + "[familyId]", String.valueOf(familyId));
            form.add(on + "[payerId]", String.valueOf(payerId));
        }
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
        } catch (RestClientException e) {
            throw new IllegalStateException("Card payments are not available just now. Please try again later.", e);
        }
    }

    @Override
    public String createPortal(String customerId, String returnUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId);
        form.add("return_url", returnUrl);
        try {
            Portal portal = api.post().uri("/billing_portal/sessions")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Portal.class);
            if (portal == null || portal.url() == null) {
                throw new IllegalStateException("Stripe did not open the page. Please try again.");
            }
            return portal.url();
        } catch (RestClientException e) {
            throw new IllegalStateException("Managing the subscription is not available just now. Please try again later.",
                    e);
        }
    }

    @Override
    public void cancelSubscription(String subscriptionId, boolean atPeriodEnd) {
        try {
            if (atPeriodEnd) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("cancel_at_period_end", "true");
                api.post().uri("/subscriptions/{id}", subscriptionId)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
            } else {
                api.delete().uri("/subscriptions/{id}", subscriptionId).retrieve().toBodilessEntity();
            }
        } catch (RestClientException e) {
            throw new IllegalStateException("Stripe did not cancel the subscription. Nothing has been changed.", e);
        }
    }

    @Override
    public String invoicePaymentIntent(String invoiceId) {
        try {
            InvoicePayments page = api.get()
                    .uri(uri -> uri.path("/invoice_payments").queryParam("invoice", invoiceId)
                            .queryParam("status", "paid").queryParam("limit", 10).build())
                    .retrieve().body(InvoicePayments.class);
            if (page == null || page.data() == null) {
                return null;
            }
            return page.data().stream().map(InvoicePayment::payment)
                    .filter(p -> p != null && p.payment_intent() != null).map(PaidWith::payment_intent).findFirst()
                    .orElse(null);
        } catch (RestClientException e) {
            // The month is still recorded; without the payment intent a refund of it is made by hand.
            return null;
        }
    }

    @Override
    public Refund createRefund(String paymentIntent, int amountMinor, Long paymentId, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntent);
        form.add("amount", String.valueOf(amountMinor));
        form.add("metadata[paymentId]", String.valueOf(paymentId));
        try {
            Refund refund = api.post().uri("/refunds").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Idempotency-Key", idempotencyKey).body(form).retrieve().body(Refund.class);
            if (refund == null || refund.id() == null || "failed".equals(refund.status())
                    || "canceled".equals(refund.status())) {
                throw new IllegalStateException("Stripe did not make the refund. Nothing has been recorded.");
            }
            return refund;
        } catch (RestClientException e) {
            throw new IllegalStateException("Stripe did not make the refund. Nothing has been recorded.", e);
        }
    }
}
