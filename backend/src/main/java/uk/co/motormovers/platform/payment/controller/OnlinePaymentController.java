package uk.co.motormovers.platform.payment.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.payment.service.OnlinePaymentService;

/** Paying by card with Stripe Checkout, and Stripe's confirmation (the webhook, open to Stripe only by signature). */
@RestController
public class OnlinePaymentController {

    private final OnlinePaymentService online;

    public OnlinePaymentController(OnlinePaymentService online) {
        this.online = online;
    }

    /** Whether Pay by card is offered. */
    @GetMapping("/api/payments/online")
    public Map<String, Boolean> enabled() {
        return Map.of("enabled", online.enabled());
    }

    @PostMapping("/api/payments/{paymentId}/checkout")
    public Map<String, String> checkout(@PathVariable Long paymentId) {
        return Map.of("url", online.checkout(paymentId));
    }

    @PostMapping("/api/stripe/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        online.webhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
