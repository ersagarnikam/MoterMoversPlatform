package uk.co.motormovers.platform.payment.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.payment.model.PayerType;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentRefund;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentStatus;

/**
 * Amounts are in the currency's minor unit (pence or cents; the {@code *Pence} names stay). "Paid" is money received,
 * net of refunds; "outstanding" is invoiced and not yet paid.
 */
public final class PaymentViews {

    private PaymentViews() {
    }

    /**
     * One payment. payerEmail is null when a school pays; paidOnline when it was paid by card on the platform.
     * taxPence is the tax included in the amount; null on payments from before it was kept, which show no tax line.
     * refundedPence is what has gone back, and refundLabel "Refunded" or "Part refunded" (null with nothing refunded).
     */
    public record PaymentView(Long id, PayerType payerType, String payerName, String payerEmail, Long schoolId,
            PaymentPurpose purpose, String purposeLabel, String description, int amountPence, PaymentStatus status,
            LocalDate invoicedOn, LocalDate paidOn, String reference, boolean paidOnline, Integer taxPence,
            int refundedPence, String refundLabel, List<RefundView> refunds) {

        public static PaymentView of(Payment p) {
            boolean school = p.getSchool() != null;
            return new PaymentView(p.getId(), p.payerType(),
                    school ? p.getSchool().getName() : p.getPayer().getDisplayName(),
                    school ? null : p.getPayer().getEmail(), school ? p.getSchool().getId() : null, p.getPurpose(),
                    p.getPurpose().label(), p.getDescription(), p.getAmountPence(), p.getStatus(), p.getInvoicedOn(),
                    p.getPaidOn(), p.getReference(), p.getPaidOnlineRef() != null, p.taxPence(), p.refundedPence(),
                    p.refundLabel(), p.getRefunds().stream().map(RefundView::of).toList());
        }
    }

    /** A refund and its credit note. failed: Stripe could not return it, so it no longer counts. */
    public record RefundView(Long id, int amountPence, Integer taxPence, String reason, LocalDate refundedOn,
            String creditNote, boolean byHand, boolean failed) {

        public static RefundView of(PaymentRefund r) {
            return new RefundView(r.getId(), r.getAmountPence(), r.getTaxPence(), r.getReason(), r.getRefundedOn(),
                    r.creditNoteLabel(), r.byHand(), !r.counts());
        }
    }

    /** The team refunds all or part of a paid payment. clientId makes sending it twice safe. */
    public record RefundIn(@Min(1) @Max(10_000_000) int amountPence, @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 64) String clientId) {
    }

    public record GroupTotal(String key, String label, long paidPence, long outstandingPence, int payments) {
    }

    /** month is YYYY-MM, counted by the date the money arrived. */
    public record MonthTotal(String month, long paidPence) {
    }

    public record SchoolTotal(Long schoolId, String schoolName, String trustName, long paidPence,
            long outstandingPence) {
    }

    public record TrustTotal(Long trustId, String trustName, int schools, long paidPence, long outstandingPence) {
    }

    public record PersonTotal(Long userId, String name, String email, PayerType payerType, long paidPence,
            long outstandingPence) {
    }

    /** Everything, for the Motor Movers team. byMonth covers the last 12 months, oldest first. */
    public record AdminSummary(long paidPence, long outstandingPence, long paidLast12MonthsPence,
            List<GroupTotal> byPayerType, List<GroupTotal> byPurpose, List<MonthTotal> byMonth,
            List<SchoolTotal> bySchool, List<TrustTotal> byTrust, List<PersonTotal> byPerson) {
    }

    /** What a school has paid. Staff who pay for their own courses are not included. */
    public record SchoolPayments(Long schoolId, String schoolName, long paidPence, long outstandingPence,
            List<PaymentView> payments) {
    }

    /** A trust's spend, school by school. Totals only, like the rest of the trust view. */
    public record TrustPayments(Long trustId, String trustName, long paidPence, long outstandingPence,
            List<SchoolTotal> schools) {
    }

    /** What the signed-in person has paid themselves. */
    public record MyPayments(long paidPence, long outstandingPence, List<PaymentView> payments) {
    }

    /**
     * A payment the team records. Give a school or a person's email, not both. Leave paidOn empty for an
     * invoice that is still due.
     */
    public record NewPayment(Long schoolId, @Email @Size(max = 200) String payerEmail, @NotNull PaymentPurpose purpose,
            @NotBlank @Size(max = 200) String description, @Min(1) @Max(10_000_000) int amountPence,
            @NotNull LocalDate invoicedOn, @Size(max = 100) String reference, LocalDate paidOn) {
    }

    public record MarkPaid(@NotNull LocalDate paidOn) {
    }
}
