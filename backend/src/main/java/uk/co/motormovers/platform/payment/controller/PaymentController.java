package uk.co.motormovers.platform.payment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.payment.dto.PaymentViews;
import uk.co.motormovers.platform.payment.service.PaymentService;

/**
 * Payments and spending summaries. Recording payments and the full summary are under /api/admin (ADMIN
 * only); the school, trust and personal views go through AccessPolicy in the service.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/api/admin/payments")
    public List<PaymentViews.PaymentView> all() {
        return payments.all();
    }

    @GetMapping("/api/admin/payments/summary")
    public PaymentViews.AdminSummary summary() {
        return payments.summary();
    }

    @PostMapping("/api/admin/payments")
    public PaymentViews.PaymentView record(@Valid @RequestBody PaymentViews.NewPayment request) {
        return payments.record(request);
    }

    @PostMapping("/api/admin/payments/{paymentId}/paid")
    public PaymentViews.PaymentView markPaid(@PathVariable Long paymentId,
            @Valid @RequestBody PaymentViews.MarkPaid request) {
        return payments.markPaid(paymentId, request.paidOn());
    }

    @PostMapping("/api/admin/payments/{paymentId}/cancel")
    public PaymentViews.PaymentView cancel(@PathVariable Long paymentId) {
        return payments.cancel(paymentId);
    }

    @GetMapping("/api/schools/{schoolId}/payments")
    public PaymentViews.SchoolPayments school(@PathVariable Long schoolId) {
        return payments.forSchool(schoolId);
    }

    @GetMapping("/api/trusts/{trustId}/payments")
    public PaymentViews.TrustPayments trust(@PathVariable Long trustId) {
        return payments.forTrust(trustId);
    }

    @GetMapping("/api/payments/mine")
    public PaymentViews.MyPayments mine() {
        return payments.mine();
    }
}
