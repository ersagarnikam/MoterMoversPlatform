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
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentStatus;

/** Amounts are in pence. "Paid" is money received; "outstanding" is invoiced and not yet paid. */
public final class PaymentViews {

    private PaymentViews() {
    }

    /** An amount as people read it, as on the web: "£45", or "£45.50" when there are pence. */
    public static String pounds(long pence) {
        return pence % 100 == 0 ? String.format(java.util.Locale.UK, "£%,d", pence / 100)
                : String.format(java.util.Locale.UK, "£%,.2f", pence / 100.0);
    }

    /** One payment. payerEmail is null when a school pays. */
    public record PaymentView(Long id, PayerType payerType, String payerName, String payerEmail, Long schoolId,
            PaymentPurpose purpose, String purposeLabel, String description, int amountPence, PaymentStatus status,
            LocalDate invoicedOn, LocalDate paidOn, String reference) {

        public static PaymentView of(Payment p) {
            boolean school = p.getSchool() != null;
            return new PaymentView(p.getId(), p.payerType(),
                    school ? p.getSchool().getName() : p.getPayer().getDisplayName(),
                    school ? null : p.getPayer().getEmail(), school ? p.getSchool().getId() : null, p.getPurpose(),
                    p.getPurpose().label(), p.getDescription(), p.getAmountPence(), p.getStatus(), p.getInvoicedOn(),
                    p.getPaidOn(), p.getReference());
        }
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
