package uk.co.motormovers.platform.payment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.payment.dto.PaymentViews;
import uk.co.motormovers.platform.payment.service.PaymentService;
import uk.co.motormovers.platform.payment.service.RefundService;

/**
 * Payments and spending summaries. Recording payments and the full summary are under /api/admin (ADMIN
 * only); the school, trust and personal views go through AccessPolicy in the service.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;
    private final RefundService refunds;

    public PaymentController(PaymentService payments, RefundService refunds) {
        this.payments = payments;
        this.refunds = refunds;
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

    @PostMapping("/api/admin/payments/{paymentId}/refund")
    public PaymentViews.PaymentView refund(@PathVariable Long paymentId,
            @Valid @RequestBody PaymentViews.RefundIn request) {
        return refunds.refund(paymentId, request);
    }

    /** The credit note, for the team, the school's head or the person who paid (checked in the service). */
    @GetMapping("/api/payments/refunds/{refundId}/credit-note.pdf")
    public ResponseEntity<byte[]> creditNote(@PathVariable Long refundId) {
        RefundService.CreditNote note = refunds.creditNote(refundId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(note.fileName()).build().toString())
                .body(note.pdf());
    }

    /** Emails the credit note to the signed-in person: the parent app cannot save a file. */
    @PostMapping("/api/payments/refunds/{refundId}/credit-note/email")
    public ResponseEntity<Void> emailCreditNote(@PathVariable Long refundId) {
        refunds.emailCreditNote(refundId);
        return ResponseEntity.noContent().build();
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
